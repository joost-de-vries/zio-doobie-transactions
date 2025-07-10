package demo

import com.zaxxer.hikari.HikariDataSource
import demo.Database.hikariDataSource
import demo.{Config, Database, Main}
import doobie.*
import doobie.free.KleisliInterpreter
import doobie.syntax.all.*
import doobie.util.log.LogHandler
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import zio.interop.catz.*
import zio.{Cause, Scope, Task, URIO, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, Config as _, Duration as _, *}

import java.sql.{Connection, SQLException}
import javax.sql.DataSource

object DoobieZio2 extends ZIOAppDefault:

  // code that takes part in a transaction doesn't have a different type. Just Task[A]

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] = for
    _ <- ZIO.logInfo("ZIO doobie using FiberRef")
    dataSource <- setup

    (result1, result2) <- dataSource.transactional:
      for
        first <- sql"SELECT 1".query[Int].unique.toZio

        savepoint <- withConnection(_.setSavepoint("my savepoint")) // use the jdbc connection
        _ <- ZIO.logInfo(s"saved point '${savepoint.getSavepointName}'") // do some zio effect

        second <- sql"SELECT random()".query[Double].unique.toZio
      yield (first, second)

    _ <- ZIO.logInfo(s"Result $result1, $result2")
  yield ()

  extension [A](sqlProgram: ConnectionIO[A])
    def toZio: Task[A] =
      withConnectionZio(connection => sqlProgram.foldMap(interp).run.apply(connection))

  extension (dataSource: DataSource)
    def transactional[A](task: Task[A]): Task[A] =
      withConnectionZio:
        for
          _ <- withConnection(_.setAutoCommit(false))
          result <- task.orRollback
          _ <- withConnection(_.commit())
        yield result

    def withConnectionZio[A](task: Task[A]): Task[A] =
      ZIO.scoped[Any]:
        for
          connection <- ZIO.fromAutoCloseable(ZIO.attemptBlocking(dataSource.getConnection()))
          result <- currentConnection.locallyWith {
            case s @ Some(c) => println(s"already tx"); s
            case None        => Some(connection)
          }(task)
        yield result

  def withConnection[A](task: Connection => A): Task[A] =
    withConnectionZio(c => ZIO.attemptBlocking(task(c)))

  def withConnectionZio[R, E, A](task: Connection => ZIO[R, E, A]): ZIO[R, E, A] =
    currentConnection.get.flatMap:
      case Some(connection) => task(connection)
      case None             => ZIO.die(Exception(s"No transaction started"))

  val currentConnection: FiberRef[Option[Connection]] =
    Unsafe.unsafe { implicit unsafe =>
      FiberRef.unsafe.make(None)
    }

  private lazy val interp = KleisliInterpreter[Task](LogHandler.noop).ConnectionInterpreter

  extension [A](task: Task[A])
    def orRollback: Task[A] = task.sandbox
      .mapError(_.untraced)
      .catchAll:
        case cause @ Cause.Fail(t: SQLException, _) =>
          withConnection(_.rollback()) *> ZIO.failCause(cause)
        case cause =>
          withConnection(_.rollback()) *> ZIO.failCause(cause)

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Main.logging

  def setup: URIO[Scope, HikariDataSource] =
    (for
      config <- Config().map(_.database)
      dataSource <- hikariDataSource(config, SimpleMeterRegistry())
        .tapError(e => ZIO.logErrorCause(s"Failed to create HikariDataSource: ${e.getMessage}", Cause.die(e)))
        .retry(Schedule.spaced(1.seconds) && Schedule.recurs(60))
    yield dataSource).orDie

end DoobieZio2

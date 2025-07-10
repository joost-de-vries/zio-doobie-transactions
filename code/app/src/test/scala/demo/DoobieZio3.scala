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

object DoobieZio3 extends ZIOAppDefault:

  // code that takes part in a transaction is a scala 3 context function
  type Transactional[A] = Connection ?=> Task[A]

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] = for
    _ <- ZIO.logInfo("ZIO doobie using implicit functions")
    dataSource <- setup

    (result1, result2) <- dataSource.transactional: connection ?=> // if we don't use the connection we can leave this out
      for
        first <- sql"SELECT 1".query[Int].unique.toZio

        savepoint <- ZIO.attemptBlocking(connection.setSavepoint("my savepoint"))
        _ <- ZIO.logInfo(s"saved point '${savepoint.getSavepointName}'")

        second <- sql"SELECT random()".query[Double].unique.toZio
      yield (first, second)

    _ <- ZIO.logInfo(s"Result $result1, $result2")
  yield ()

  extension [A](doobieProgram: ConnectionIO[A]) def toZio: Transactional[A] = connection ?=> doobieProgram.foldMap(interp).run.apply(connection)

  extension (dataSource: DataSource)
    def transactional[A](task: Transactional[A]): Task[A] =
      withConnection: connection ?=>
        for
          _ <- ZIO.attemptBlocking(connection.setAutoCommit(false))
          result <- task.orRollback
          _ <- ZIO.attemptBlocking(connection.commit())
        yield result

    def withConnection[A](task: Transactional[A]): Task[A] =
      ZIO.scoped[Any]:
        for
          connection <- ZIO.fromAutoCloseable(ZIO.attemptBlocking(dataSource.getConnection()))
          result <- task.apply(using connection)
        yield result

  private lazy val interp = KleisliInterpreter[Task](LogHandler.noop).ConnectionInterpreter

  extension [A](task: Transactional[A])
    def orRollback: Transactional[A] = task.sandbox
      .mapError(_.untraced)
      .catchAll:
        case cause @ Cause.Fail(t: SQLException, _) =>
          ZIO.attemptBlocking(summon[Connection].rollback()) *> ZIO.failCause(cause)
        case cause =>
          ZIO.attemptBlocking(summon[Connection].rollback()) *> ZIO.failCause(cause)

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Main.logging

  def setup: URIO[Scope, HikariDataSource] =
    (for
      config <- Config().map(_.database)
      dataSource <- hikariDataSource(config, SimpleMeterRegistry())
        .tapError(e => ZIO.logErrorCause(s"Failed to create HikariDataSource: ${e.getMessage}", Cause.die(e)))
        .retry(Schedule.spaced(1.seconds) && Schedule.recurs(60))
    yield dataSource).orDie

end DoobieZio3

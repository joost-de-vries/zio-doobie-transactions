package demo

import cats.*
import cats.data.Kleisli
import cats.effect.*
import cats.effect.implicits.*
import com.zaxxer.hikari.HikariDataSource
import demo.Database.hikariDataSource
import demo.{Config, Logging}
import doobie.*
import doobie.free.KleisliInterpreter
import doobie.implicits.*
import doobie.syntax.all.*
import doobie.util.log.LogHandler
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import zio.interop.catz.*
import zio.{Cause, Scope, Task, URIO, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, Config as _, Duration as _, *}

import java.sql.{Connection, SQLException}
import javax.sql.DataSource

object DoobieZio1 extends ZIOAppDefault:

  // zio code that takes part in the transaction
  // has a type with Connection in the environment
  type Transactional[A] = RIO[Connection, A]

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] = for
    _ <- ZIO.logInfo("ZIO doobie using environment")
    dataSource <- setup

    (result1, result2) <- dataSource.transactional:
      for
        first <- sql"SELECT 1".query[Int].unique.toZio // Transactional[Int]

        savepoint <- withConnection(_.setSavepoint("my savepoint")) // use the jdbc connection
        _ <- ZIO.logInfo(s"saved point '${savepoint.getSavepointName}'") // do some zio effect

        second <- sql"SELECT random()".query[Double].unique.toZio // another sql
      yield (first, second)

    _ <- ZIO.logInfo(s"Result $result1, $result2")
  yield ()

  extension [A](sqlProgram: ConnectionIO[A])
    def toZio: Transactional[A] =
      ZIO.serviceWithZIO[Connection](connection => sqlProgram.foldMap(interp).run.apply(connection))

  def withConnection[A](task: Connection => A): Transactional[A] =
    ZIO.serviceWithZIO[Connection](connection => ZIO.attemptBlocking(task(connection)))

  extension (dataSource: DataSource)
    def transactional[A](prog: Transactional[A]): Task[A] =
      withConnectionZio[A]:
        for
          _ <- withConnection(_.setAutoCommit(false))
          result <- prog.orRollback
          _ <- withConnection(_.commit())
        yield result

    def withConnectionZio[A](task: Transactional[A]): Task[A] =
      ZIO.scoped[Any]:
        for
          connection <- ZIO.fromAutoCloseable(ZIO.attemptBlocking(dataSource.getConnection()))
          result <- task.provide(ZLayer.succeed(connection))
        yield result

  extension [A](prog: Transactional[A])
    def orRollback: Transactional[A] = prog.sandbox
      .mapError(_.untraced)
      .catchAll:
        case cause @ Cause.Fail(t: SQLException, _) =>
          withConnection(_.rollback()) *> ZIO.failCause(cause)
        case cause =>
          withConnection(_.rollback()) *> ZIO.failCause(cause)

  private lazy val interp = KleisliInterpreter[Task](LogHandler.noop).ConnectionInterpreter

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Logging.logging

  def setup: URIO[Scope, HikariDataSource] =
    (for
      config <- Config().map(_.database)
      dataSource <- hikariDataSource(config, SimpleMeterRegistry())
        .tapError(e => ZIO.logErrorCause(s"Failed to create HikariDataSource: ${e.getMessage}", Cause.die(e)))
        .retry(Schedule.spaced(1.seconds) && Schedule.recurs(60))
    yield dataSource).orDie

end DoobieZio1

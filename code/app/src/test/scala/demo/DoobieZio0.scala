package demo

import cats.*
import cats.data.*
import demo.{Config, Database, Logging}
import doobie.*
import doobie.free.KleisliInterpreter
import doobie.free.connection.ConnectionOp
import doobie.implicits.*
import doobie.syntax.all.*
import doobie.util.log.LogHandler
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import zio.interop.catz.*
import zio.{Scope, Task, URIO, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer, Config as _, Duration as _}

import java.sql.Connection

object DoobieZio0 extends ZIOAppDefault:

  // zio code is type ZIO[R,E,A]
  // and code that takes part in a transaction is ConnectionIO. So separate

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] = for
    _ <- ZIO.logInfo("ZIO doobie")
    transactor <- setup

    (result1, result2) <- sqlProgram.transact(transactor)
    _ <- ZIO.logInfo(s"Result $result1, $result2")
  yield ()

  // we can't intersperse sql with the rest of our program. so separate method
  def sqlProgram: ConnectionIO[(Int, Double)] = for
    first <- sql"SELECT 1".query[Int].unique // ConnectionIO[Int]

    savepoint <- FC.setSavepoint("my savepoint") // use the jdbc connection
    // _ <- ZIO.logInfo(s"saved point '${savepoint.getSavepointName}'") // can't do zio effects

    second <- sql"SELECT random()".query[Double].unique // another sql
  yield (first, second)

  // Connection => Task[A]
  type Transactional[A] = Kleisli[Task, Connection, A] // a Kleisli is a monadic form of A => B

  // step by step
  def stepByStep[A](): Task[A] =
    // a Transactor holds
    // 1. a source for Connections
    def connection: Connection = ???
    // 2. an interpreter for ConnectionIO
    // interprets the sql program to something we can run against a Connection
    // the ConnectionIO in itself carries no specific meaning
    // ~> is a FunctionK. transforms a functor   f.i. List to Option : headOption or ZIO.fromTry
    val interpreter: ConnectionOp ~> Transactional = ???
    // 3. a Strategy for initialisation, onError and cleanup (ie setAutoCommit(false) and commit or rollback and close

    // a ConnectionIO[A] is a Free[ConnectionOp, A]
    // a Free monad over functor ConnectionOp
    // ConnectionOp is an algebra for operations on Connection
    // ie queries and statements. but also setAutoCommit, setSavepoint etc
    val connectionIo: ConnectionIO[A] = ???

    // a wrapper around Connection => Task[A]
    val interpreted: Kleisli[Task, Connection, A] = connectionIo.foldMap(interpreter)
    val function: Connection => Task[A] = interpreted.run
    val result: Task[A] = function.apply(connection)

    result
  end stepByStep

  private lazy val interp: ConnectionOp ~> Transactional = KleisliInterpreter[Task](LogHandler.noop).ConnectionInterpreter

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = Logging.logging

  def setup: URIO[Scope, Transactor[Task]] =
    for
      config <- Config().map(_.database)
      (tx, _) <- Database.transactor(config, SimpleMeterRegistry())
    yield tx

end DoobieZio0

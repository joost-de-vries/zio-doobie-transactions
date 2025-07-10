package demo

import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.hikari.HikariTransactor
import doobie.*
import io.micrometer.core.instrument.MeterRegistry
import org.flywaydb.core.Flyway
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension
import org.slf4j.LoggerFactory
import pureconfig.*
import zio.interop.catz.*
import zio.{Config as _, Duration as _, *}

import java.util.concurrent.{ExecutorService, Executors, TimeUnit}
import javax.sql.DataSource
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

case class DatabaseConfig(
    host: String,
    port: Int,
    db: String,
    user: String,
    password: String,
    connectionTimeout: FiniteDuration,
    statementTimeout: FiniteDuration,
    maxLifetime: FiniteDuration,
    socketTimeout: FiniteDuration,
    maximumPoolSize: Int,
    poolName: String,
    migrationLocations: List[String],
    flywayTransactionalLock: Boolean
) derives ConfigReader:

  private val baseJdbcUrl: String = s"jdbc:postgresql://$host:$port/$db"

  def jdbcUrl: String = s"$baseJdbcUrl?options=-c%20statement_timeout=${statementTimeout.toMillis}"

object Database:

  def setup: URIO[Scope & MeterRegistry, Transactor[Task]] =
    (for
      config <- Config().map(_.database)
      meterRegistry <- ZIO.service[MeterRegistry]
      (tx, dataSource) <- transactor(config, meterRegistry)
      _ <- migrate(dataSource, config)
    yield tx).orDie

  def transactor(config: DatabaseConfig, meterRegistry: MeterRegistry): URIO[Scope, (Transactor[Task], HikariDataSource)] =
    (for
      dataSource <- hikariDataSource(config, meterRegistry)
        .tapError(e => ZIO.logErrorCause(s"Failed to create HikariDataSource: ${e.getMessage}", Cause.die(e)))
        .retry(Schedule.spaced(1.seconds) && Schedule.recurs(60))
      tx <- fixedThreadPool(dataSource.getMaximumPoolSize, dataSource.getConnectionTimeout).map(pool => HikariTransactor[Task](dataSource, pool))
    yield (tx, dataSource)).orDie

  private def migrate(dataSource: DataSource, config: DatabaseConfig): Task[Unit] =
    for
      _ <- ZIO.logInfo("Starting Flyway migrations")
      _ <- ZIO.attemptBlocking {
        val flyway = Flyway.configure().locations(config.migrationLocations*).dataSource(dataSource)

        // set flywayTransactionalLock to false to allow 'concurrently' in index creation
        // see https://stackoverflow.com/questions/20350501/how-do-i-create-indexes-concurrently-with-flyway-in-postgres/73928224#73928224
        flyway.getPluginRegister
          .getPlugin(classOf[PostgreSQLConfigurationExtension])
          .setTransactionalLock(config.flywayTransactionalLock)

        flyway.load().migrate()
      }
      _ <- ZIO.logInfo("Finished Flyway migrations")
    yield ()

  def hikariDataSource(config: DatabaseConfig, meterRegistry: MeterRegistry): RIO[Scope, HikariDataSource] =
    val hikariConfig = new HikariConfig()
    hikariConfig.setPoolName(config.poolName)
    hikariConfig.setJdbcUrl(config.jdbcUrl)
    hikariConfig.setUsername(config.user)
    hikariConfig.setPassword(config.password)
    hikariConfig.setDriverClassName("org.postgresql.Driver")
    hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
    hikariConfig.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(meterRegistry))
    hikariConfig.setConnectionTimeout(config.connectionTimeout.toMillis)
    hikariConfig.setMaxLifetime(config.maxLifetime.toMillis + 1)
    hikariConfig.setKeepaliveTime(config.maxLifetime.toMillis)
    hikariConfig.setConnectionInitSql("select 1")
    hikariConfig.setConnectionTestQuery("select 1")
    hikariConfig.addDataSourceProperty("socketTimeout", config.socketTimeout.toSeconds)
    hikariConfig.addDataSourceProperty("tcpKeepAlive", true)
    ZIO.acquireRelease(ZIO.attempt(new HikariDataSource(hikariConfig)))(ds => ZIO.succeed(ds.close()))

  private def fixedThreadPool(maximumPoolSize: Int, terminationTimeoutMs: Long): ZIO[Scope, Throwable, ExecutionContextExecutor] =
    ZIO.acquireRelease(ZIO.attempt(Executors.newFixedThreadPool(maximumPoolSize)))(shutdown(terminationTimeoutMs)).map(ExecutionContext.fromExecutor(_, reportFailure))

  private lazy val logger = LoggerFactory.getLogger(getClass)

  private def reportFailure(t: Throwable): Unit =
    logger.error("An uncaught error occurred in a task submitted to the ExecutionContext", t)

  private def shutdown(terminationTimeoutMs: Long)(es: ExecutorService) =
    ZIO.succeed(es.shutdown()) *> ZIO
      .attemptBlocking(es.awaitTermination(terminationTimeoutMs, TimeUnit.MILLISECONDS))
      .orDie
      .flatMap { terminated =>
        ZIO.logWarning(s"Timed out waiting for ExecutorService to shutdown within $terminationTimeoutMs milliseconds").unless(terminated)
      }

end Database

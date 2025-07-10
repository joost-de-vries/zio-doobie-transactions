import sbt.*

object Dependencies {

  object Doobie {

    val doobieVersion = "1.0.0-RC9"
    val flywayVersion = "11.10.0"

    val dependencies: Seq[ModuleID] = Seq(
      "org.postgresql" % "postgresql" % "42.7.7",
      "com.zaxxer" % "HikariCP" % "6.3.0",
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres-circe" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "org.flywaydb" % "flyway-core" % flywayVersion,
      "org.flywaydb" % "flyway-database-postgresql" % flywayVersion
    )
  }

  object PureConfig {

    val pureConfigVersion = "0.17.9"

    val dependencies: Seq[ModuleID] = Seq(
      "com.github.pureconfig" %% "pureconfig-core" % pureConfigVersion
    )
  }

  object Zio {
    
    val version = "2.1.19"
    val loggingVersion = "2.5.0"

    val dependencies: Seq[ModuleID] = Seq(
      "dev.zio" %% "zio" % version,
      "dev.zio" %% "zio-logging" % loggingVersion,
      "dev.zio" %% "zio-logging-slf4j2" % loggingVersion,
      "dev.zio" %% "zio-test" % version % "test",
      "dev.zio" %% "zio-test-sbt" % version % "test",
      "dev.zio" %% "zio-interop-cats" % "23.1.0.5"
    ) ++ Metrics.dependencies
  }


  object Logging {
    
    val dependencies: Seq[ModuleID] = Seq(
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "net.logstash.logback" % "logstash-logback-encoder" % "8.1",
      "com.lihaoyi" %% "pprint" % "0.9.0"
    )
  }

  object Metrics {
    val dependencies: Seq[ModuleID] = Seq(
      "dev.zio"                       %% "zio-metrics-connectors-micrometer"      % "2.3.1",
      "io.micrometer"                  % "micrometer-registry-prometheus"         % "1.15.1",
      "io.prometheus"                  % "prometheus-metrics-simpleclient-bridge" % "1.3.8",
    )
  }

}

import Dependencies.{ *}
import sbt.{Def, ThisBuild}

import java.io
import java.nio.charset.StandardCharsets
import scala.sys.process.*

val scalaVersionValue = "3.7.1"

ThisBuild / scalaVersion := scalaVersionValue
ThisBuild / scalacOptions ++= Seq("-language:strictEquality")
ThisBuild / scalaFmtRunnerDialect := "scala3"
ThisBuild / disableDhlScalafmtSettings := true
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / publish / skip := true
ThisBuild / publishLocal / skip := true

Global / lintUnusedKeysOnLoad := false
Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = project
  .in(file("."))
  .settings(
    sharedSettings,
    name := "Doobie transactional",
  )
  .aggregate(app)

lazy val app = project
  .in(file("app"))
  .enablePlugins(BuildInfoPlugin)
  .settings(sharedSettings)
  .settings(
    name := "Doobie zio",
    buildInfoKeys := Seq[BuildInfoKey](moduleName, version),
    libraryDependencies ++=
      PureConfig.dependencies ++
        Doobie.dependencies ++
        Zio.dependencies++
        Logging.dependencies
  )


val sharedSettings: Seq[Def.Setting[?]] = Seq(
  scalaVersion := scalaVersionValue,
  scalacOptions := Seq(),
)



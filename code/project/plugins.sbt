import sbt.librarymanagement.ivy.Credentials.toDirect

addSbtPlugin("de.gccc.sbt" % "sbt-jib" % "1.4.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.2")
addSbtPlugin("nl.gn0s1s" % "sbt-dotenv" % "3.1.1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.3.1")
addSbtPlugin("org.openapitools" % "sbt-openapi-generator" % "7.13.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.5")
libraryDependencies += "com.google.cloud.tools" % "jib-core" % "0.27.3"

addDependencyTreePlugin

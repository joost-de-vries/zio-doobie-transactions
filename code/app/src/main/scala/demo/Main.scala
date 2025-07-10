package demo

import zio.logging.backend.SLF4J
import zio.{Exit, Runtime, Scope, ULayer, URLayer, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

object Main:

  val logging: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

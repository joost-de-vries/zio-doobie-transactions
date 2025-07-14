package demo

import zio.logging.backend.SLF4J
import zio.{Runtime, ULayer, ZLayer}

object Logging:

  val logging: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

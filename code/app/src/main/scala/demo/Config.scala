package demo

import pureconfig.*
import zio.{UIO, ULayer, ZIO, ZLayer}

import scala.concurrent.duration.Duration

case class Config(
    database: DatabaseConfig,
) derives ConfigReader

object Config:

  def apply(): UIO[Config] =
    ZIO
      .fromEither(ConfigSource.default.load[Config])
      .mapError(f => new IllegalArgumentException(s"Failed to load config: ${f.prettyPrint()}"))
      .orDie

  val layer: ULayer[Config] = ZLayer.fromZIO(Config())

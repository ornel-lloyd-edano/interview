package forex.config

import cats.effect.Sync
import fs2.Stream
import pureconfig.ConfigSource
import pureconfig.generic.auto._


object Config {

  def applicationConfig(path: String) = ConfigSource.default.at(path).loadOrThrow[ApplicationConfig]
  /**
   * @param path the property path inside the default configuration
   */
  def stream[F[_]: Sync](path: String): Stream[F, ApplicationConfig] = {
    Stream.eval(Sync[F].delay(applicationConfig(path)))
  }

}

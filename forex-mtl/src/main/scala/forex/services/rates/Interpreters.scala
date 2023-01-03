package forex.services.rates

import cats.Applicative
import cats.effect.Sync
import forex.config.ApplicationConfig
import interpreters._
import org.http4s.client.Client

import java.time.Clock

object Interpreters {
  def dummy[F[_]: Applicative](clock: Clock): Algebra[F] = new OneFrameDummy[F](clock)
  def live[F[_]: Sync](httpClient: Client[F], config: ApplicationConfig, clock: Clock): Algebra[F] =
    new OneFrameLiveClient(httpClient, config, clock)
}

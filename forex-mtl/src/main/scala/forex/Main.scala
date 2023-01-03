package forex

import scala.concurrent.ExecutionContext
import cats.effect._
import com.google.common.cache.CacheBuilder
import forex.config._
import forex.domain.Rate
import forex.http.rates.RatesHttpRoutes
import forex.programs.{AuthProgram, Error, RatesProgram}
import forex.services.RatesServices
import forex.services.auth.Authenticator
import forex.services.limiter.interpreters.RequestLimiter
import forex.services.repo.UserRepository
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.blaze.BlazeServerBuilder
import scalacache._
import guava._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO].stream(executionContext).compile.drain.as(ExitCode.Success)

}

class Application[F[_]: ConcurrentEffect: Timer] {
  def stream(ec: ExecutionContext): Stream[F, Unit] = {
    for {
      config <- Config.stream("app")
      httpClient <- BlazeClientBuilder[F](ec).stream
      clock = java.time.Clock.systemUTC()

      underlyingCache = CacheBuilder.newBuilder()
        .maximumSize(Integer.MAX_VALUE)
        .build[String, Entry[Either[Error, List[Rate]]]]
      cache = GuavaCache(underlyingCache)
      ratesService = RatesServices.live[F](httpClient, config, clock)
      requestLimiter = new RequestLimiter[F]()
      userRepo = new UserRepository[F](config)
      authenticator = new Authenticator[F](userRepo, config)
      ratesProgram = RatesProgram[F](ratesService, requestLimiter, authenticator, cache, config)
      authProgram = AuthProgram[F](userRepo, authenticator)
      ratesHttpRoutes = new RatesHttpRoutes[F](ratesProgram, authProgram).routes

      module = new Module[F](config, ratesHttpRoutes)
      _ <- BlazeServerBuilder[F](ec)
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()
  }

}

package forex.services.rates.interpreters

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxApplicativeId, toFunctorOps}
import cats.syntax.either._
import forex.domain.{Currency, Rate}
import forex.config.ApplicationConfig
import forex.services.rates.RateServiceResponse
import forex.services.Error
import forex.services.Error._
import forex.services.rates.Algebra
import org.http4s._
import org.http4s.client.Client

import java.time.Clock


class OneFrameLiveClient [F[_]: Sync] (httpClient: Client[F], config: ApplicationConfig, clock: Clock) extends Algebra[F] {

  override def get(exchangePairs: Seq[Rate.Pair]): F[RateServiceResponse] = {
    for {
      uri <- EitherT(getValidOneFrameUri(config.oneFrameService.uri, exchangePairs).pure[F])
      result <- EitherT(requestOneFrame(uri, exchangePairs))
    } yield result
  }.value

  private def getValidOneFrameUri(uri: String, exchangePairs: Seq[Rate.Pair]): Either[Error, Uri] = {
    Uri.fromString(uri).fold(_=> OneFrameLookupFailed(s"Invalid uri configured for One Frame service").asLeft[Uri],
      oneFrameDomain=> {
        val currencyPairs = exchangePairs.map(p=> s"${p.from}${p.to}")
        oneFrameDomain.withMultiValueQueryParams( Map("pair"-> currencyPairs)).asRight[Error]
      })
  }

  private val token = config.oneFrameService.authToken
  private val maxAgeBeforeStale = config.maxRatesAgeBeforeStale

  private def requestOneFrame(oneFrameUri: Uri, exchangePairs: Seq[Rate.Pair]): F[RateServiceResponse] = {
    val req = Request[F](
      method = Method.GET,
      uri = oneFrameUri,
      headers = Headers.of(Header("token", token))
    )
    lazy val pairsStr = exchangePairs.map(p=>s"from=${p.from} to=${p.to}")
    httpClient.run(req).use(response => response.as[OneFrameResponse]).attempt
      .map {
        case Left(_) =>
          OneFrameLookupFailed("One Frame Service may not be available").asLeft
        case Right(OneFrameResponse.Error(error)) if error.contains("Forbidden") =>
          OneFrameForbiddenAccess.asLeft

        case Right(OneFrameResponse.Error(error)) if error.contains("Invalid Currency") =>
          OneFrameCurrencyPairNotFound(pairsStr).asLeft

        case Right(OneFrameResponse.Error(error)) =>
          OneFrameLookupFailed(s"${error}: ${pairsStr.mkString(", ")}").asLeft

        case Right(OneFrameResponse.Rates(rates)) if rates.exists(r=> Rate.isStale(r.timestamp, clock, maxAgeBeforeStale)) =>
          OneFrameStaleRates(pairsStr, maxAgeBeforeStale).asLeft

        case Right(OneFrameResponse.Rates(rates)) =>
          val currencies = rates.flatMap(r=> List(r.from, r.to))
          val unsupportedCurrencies = Rate.getUnsupportedCurrencies(currencies:_*)
          if (unsupportedCurrencies.isEmpty) {
            rates.map(r=>
              Currency.fromString(r.from).flatMap { from=>
                Currency.fromString(r.to).map { to=>
                  Rate(from, to, r.price, r.timestamp)
                }
              }).flatten.asRight
          } else {
            OneFrameProxyUnsupportedCurrency(unsupportedCurrencies).asLeft
          }
      }
  }

}


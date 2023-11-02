package forex.programs.rates

import cats.Monad
import cats.data.EitherT
import cats.implicits.{ catsSyntaxApplicativeId, catsSyntaxEitherId, catsSyntaxOptionId, toFlatMapOps, toFunctorOps }
import forex.config.ApplicationConfig
import forex.domain._
import forex.programs.Error
import forex.programs.Error._
import forex.services.auth.Authenticator
import forex.services.limiter.interpreters.RequestLimiter
import forex.services.rates.RatesService
import scalacache.Cache
import scalacache.modes.sync._

/**
  * Keeps the count of requests per API token
  * Each API token can be used for MAX successful requests per day
  * If count of remaining requests for any API token is zero, then request is failed (status code 429)
  * Count of remaining requests for all API tokens will reset back to MAX at RESET_TIME
  * configurables: MAX, RESET_TIME
  * defaults: MAX=10000, RESET_TIME=24 hours
  */
class Program[F[_]: Monad](
    ratesService: RatesService[F],
    requestLimiter: RequestLimiter[F],
    authenticator: Authenticator[F],
    cache: Cache[Either[Error, List[Rate]]],
    config: ApplicationConfig
) extends Algebra[F] {

  private def callRateService(request: Protocol.GetRatesRequests): F[Either[Error, List[Rate]]] =
    ratesService
      .get(request.rateRequest.map(r => Rate.Pair(r.from, r.to)))
      .map {
        case Right(rates) =>
          cache.put(keyParts = request.toCacheKey)(
            value = rates.asRight[Error],
            ttl = config.maxRatesAgeBeforeStale.some
          )
          Right(rates)
        case Left(error) =>
          val e = toProgramError(error)
          cache.put(keyParts = request.toCacheKey)(
            value = e.asLeft[List[Rate]],
            ttl = config.cachedErrorsTtl.some
          )
          Left(e)
      }

  override def get(request: Protocol.GetRatesRequests): F[Error Either List[Rate]] = {
    val result = for {
      authenticatedToken <- EitherT(authenticator.authenticate(request.token)).leftMap(toProgramError)
      requestCount <- EitherT {
                       requestLimiter.getRemainingRequests(authenticatedToken).flatMap {
                         case Some(remainingRequests) if remainingRequests.count > 0 =>
                           remainingRequests.count.asRight[Error].pure[F]
                         case Some(remainingRequests)
                             if remainingRequests.isOlderThanResetTime(config.requestLimit.resetTime) =>
                           requestLimiter
                             .upsertRequestCount(request.token, config.requestLimit.max)
                             .map(_.count.asRight[Error])
                         case Some(_) =>
                           (Error.RateLookupFailed(
                             Error.TooManyRequests,
                             "Request limit reached",
                             Some(
                               s"Token has been used for [${config.requestLimit.max}] successful requests. " +
                                 s"Use a different token or wait after [${config.requestLimit.resetTime}] to auto-reset the count."
                             )
                           ): Error).asLeft[Int].pure[F]
                         case None =>
                           requestLimiter
                             .upsertRequestCount(request.token, config.requestLimit.max)
                             .map(_.count.asRight[Error])
                       }
                     }

      rates <- EitherT {
                cache.get(request.toCacheKey).fold(callRateService(request)) {
                  case Right(cachedRates) =>
                    cachedRates.asRight[Error].pure[F]
                  case Left(cachedError) =>
                    cachedError.asLeft[List[Rate]].pure[F]
                }
              }
      _ = println(s"Request count for token [${authenticatedToken}] is $requestCount")
      updatedReqCount <- EitherT(requestLimiter.reduceRequestCount(request.token).map(_.asRight[Error]))
      _ = println(s"Request count for token [${authenticatedToken}] after request $updatedReqCount")
    } yield rates

    result.value
  }

}

object Program {

  def apply[F[_]: Monad](
      ratesService: RatesService[F],
      requestLimiter: RequestLimiter[F],
      authenticator: Authenticator[F],
      cache: Cache[Either[Error, List[Rate]]],
      config: ApplicationConfig
  ): Algebra[F] = new Program[F](ratesService, requestLimiter, authenticator, cache, config)

}

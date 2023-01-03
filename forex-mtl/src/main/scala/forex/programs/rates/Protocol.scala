package forex.programs.rates

import forex.domain.{Currency, Rate}
import forex.services.auth.Token

object Protocol {

  implicit class RatePairOps(val arg: Rate.Pair) extends AnyVal {
    def toGetRatesRequest: GetRatesRequest =
      GetRatesRequest(arg.from, arg.to)
  }

  implicit class toGetRatesRequests(val rates: Seq[Rate.Pair]) extends AnyVal {
    def toGetRatesRequests(token: String): GetRatesRequests = {
      GetRatesRequests(Token(token), rates.map(_.toGetRatesRequest))
    }
  }

  final case class GetRatesRequest(
    from: Currency,
    to: Currency
  )

  final case class GetRatesRequests(
    token: Token,
    rateRequest: Seq[GetRatesRequest]
  ) {
    def toCacheKey: Set[GetRatesRequest] = rateRequest.toSet
  }
  object GetRatesRequests {
    def apply(token: String, from: Currency, to: Currency): GetRatesRequests =
      new GetRatesRequests(Token(token), Seq(GetRatesRequest(from, to)))
  }

}

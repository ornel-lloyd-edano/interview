package forex.http.rates

//import cats.data._
//import cats.implicits._
//import cats.implicits.catsSyntaxEitherId
import forex.domain.Currency
import org.http4s.dsl.impl.OptionalMultiQueryParamDecoderMatcher
import forex.domain.Rate.Pair
import org.http4s.{ ParseFailure, QueryParamDecoder }
import org.http4s.dsl.io.ValidatingQueryParamDecoderMatcher

object QueryParams {

  val errorMsg = (currency: String) => s"Currency [$currency] is not valid or supported"

  implicit val currencyQueryParam: QueryParamDecoder[Currency] =
    QueryParamDecoder[String].emap { currencyStr =>
      Currency.fromString(currencyStr) match {
        case Some(currency) => Right(currency)
        case None           => Left(ParseFailure(errorMsg(currencyStr), errorMsg(currencyStr)))
      }
    }

  /*private[http] implicit val currencyQueryParam: QueryParamDecoder[Either[String, Currency]] =
    QueryParamDecoder[String].map(currencyStr=> Currency.fromString(currencyStr).toRight(currencyStr))*/

  object FromQueryParam extends ValidatingQueryParamDecoderMatcher[Currency]("from")
  object ToQueryParam extends ValidatingQueryParamDecoderMatcher[Currency]("to")

  /*implicit lazy val stringListQueryParamDecoder: QueryParamDecoder[List[String]] =
    new QueryParamDecoder[List[String]] {
      def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, List[String]] = {
        value.value.split(""";""").toList.map(_.validNel[ParseFailure]).sequence
      }
    }*/

  /*private[http] implicit val currencyPairQueryParam: QueryParamDecoder[Either[String, Pair]] = {
      QueryParamDecoder[String].map(currencyPairStr => {
        (currencyPairStr.size == 7, currencyPairStr.take(3), currencyPairStr.drop(4)) match {
          case (true, from, to) =>
            (for {
              fromCurrency <- Currency.fromString(from)
              toCurrency <- Currency.fromString(to)
            } yield Pair(fromCurrency, toCurrency).asRight)
              .getOrElse(currencyPairStr.asLeft)

          case _ => currencyPairStr.asLeft
        }
      })
  }*/

  implicit val currencyPairQueryParam: QueryParamDecoder[Pair] = {
    QueryParamDecoder[String].emap(currencyPairStr => {
      val from = currencyPairStr.take(3)
      val to = currencyPairStr.drop(4)
      (Currency.fromString(from), Currency.fromString(to)) match {
        case (Some(from), Some(to)) => Right(Pair(from, to))
        case (Some(_), _)           => Left(ParseFailure(errorMsg(to), errorMsg(to)))
        case _                      => Left(ParseFailure(errorMsg(from), errorMsg(from)))
      }
    })
  }

  object PairQueryParams extends OptionalMultiQueryParamDecoderMatcher[Pair]("from_to")
}

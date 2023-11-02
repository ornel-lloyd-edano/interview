package forex.domain

import cats.effect.Sync
import cats.implicits.catsSyntaxEitherId
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, DecodingFailure}
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf

import java.time.{Clock, Duration, Instant, OffsetDateTime}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.javaapi.DurationConverters


case class Rate(
  pair: Rate.Pair,
  price: Price,
  timestamp: Timestamp
) extends {
  def isStale(clock: Clock, maxAgeBeforeStale: FiniteDuration): Boolean = Rate.isStale(timestamp.value, clock, maxAgeBeforeStale)
}

object Rate {
  final case class Pair(
      from: Currency,
      to: Currency
  )

  def isStale(arg: OffsetDateTime, clock: Clock, maxAgeBeforeStale: FiniteDuration): Boolean =
    Duration.between(arg.toInstant, Instant.now(clock))
      .compareTo(DurationConverters.toJava(maxAgeBeforeStale)) == 1 //1 means Duration A > Duration B, -1 means Duration A < Duration B

  def getUnsupportedCurrencies(currencies: String*):Seq[String] =
    currencies.map(currencyStr=> currencyStr -> Currency.fromString(currencyStr))
    .filter(_._2.isEmpty).map(_._1)

  //implicit val pairDecoder: Decoder[Pair] = deriveDecoder[Pair]
  //implicit val rateDecoder: Decoder[Rate] = deriveDecoder[Rate]

  final case class ErrorResponse(error: String)
  implicit val errorDecoder: Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]

  def apply(from: Currency, to: Currency, price: Double, timestamp: OffsetDateTime): Rate =
    Rate(Pair(from, to), Price(price), Timestamp(timestamp))

  implicit val rateDecoder: Decoder[Rate] = Decoder.instance { hCursor=>
      (for {
        fromCurrency <- hCursor.downField("from").as[String]
        toCurrency <- hCursor.downField("to").as[String]
        price <- hCursor.downField("price").as[Double]
        timestamp <- hCursor.downField("time_stamp").as[OffsetDateTime]
      } yield {
        for {
          validFrom <- Currency.fromString(fromCurrency)
            .toRight(DecodingFailure(s"Got unsupported currency [$fromCurrency] from One-Frame service", Nil))
          validTo <- Currency.fromString(toCurrency)
            .toRight(DecodingFailure(s"Got unsupported currency [$toCurrency] from One-Frame service", Nil))
        } yield Rate(validFrom, validTo, price, timestamp)

      }).fold(_.asLeft, _.fold(_.asLeft, _.asRight))
  }

  //implicit val rateDecoder2: Decoder[List[Rate]] = deriveDecoder[List[Rate]]
  //implicit def rateEntityDecoder[F[_]: Sync]: EntityDecoder[F, Rate] = jsonOf
  implicit def rateEntityDecoder2[F[_]: Sync]: EntityDecoder[F, List[Rate]] = jsonOf

}

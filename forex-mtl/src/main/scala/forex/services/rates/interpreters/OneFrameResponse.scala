package forex.services.rates.interpreters

import cats.effect.Sync
import cats.implicits.catsSyntaxEitherId
import io.circe.Decoder
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf

import java.time.OffsetDateTime

sealed trait OneFrameResponse

object OneFrameResponse {

  final case class Rate(from: String, to: String, price: Double, timestamp: OffsetDateTime)
  final case class Rates(rates: List[Rate]) extends OneFrameResponse
  final case class Error(error: String) extends OneFrameResponse {
    def contains(arg: String): Boolean = error.toLowerCase.contains(arg.trim.toLowerCase)
  }

  implicit val rateDecoder: Decoder[Rate] = Decoder.instance { hCursor=>
    for {
      fromCurrency <- hCursor.downField("from").as[String]
      toCurrency <- hCursor.downField("to").as[String]
      price <- hCursor.downField("price").as[Double]
      timestamp <- hCursor.downField("time_stamp").as[OffsetDateTime]
    } yield Rate(fromCurrency, toCurrency, price, timestamp)
  }

  implicit val oneFrameRespDecoder: Decoder[OneFrameResponse] = Decoder.instance { hCursor=>
    hCursor.value.arrayOrObject(
      Error("Deserialization error").asRight,
      jsonList=> Rates(jsonList.map(_.as[Rate]).toList.collect {
        case Right(rate)=> rate
      }).asRight,
      json=> json.toList.find(_._1 == "error").headOption
        .fold[Decoder.Result[OneFrameResponse]](Error("Deserialization error").asRight) { found=>
          found._2.as[String].fold(_=> Error("Deserialization error").asRight, error=> Error(error).asRight)
        }
    )
  }

  implicit def oneFrameRespEntityDecoder[F[_]: Sync]: EntityDecoder[F, OneFrameResponse] = jsonOf

}

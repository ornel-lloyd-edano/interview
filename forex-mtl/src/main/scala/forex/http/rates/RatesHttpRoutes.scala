package forex.http
package rates

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.{catsSyntaxApplicativeId, toSemigroupKOps}
import cats.syntax.flatMap._
import forex.programs.{AuthProgram, Error, RatesProgram}
import forex.programs.rates.{Protocol => RatesProgramProtocol}
import forex.services.auth.Token
import forex.services.repo.User
import fs2.text.utf8Encode
import io.circe.syntax.EncoderOps
import org.http4s.{Header, HttpRoutes, Response, Status}
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.util.CaseInsensitiveString

class RatesHttpRoutes[F[_] : Sync](rates: RatesProgram[F], auth: AuthProgram[F]) extends Http4sDsl[F] {

  import Converters._
  import Protocol._
  import QueryParams._
  import RatesProgramProtocol._

  val toHttpErrorResponse = (error: Error) =>
    error.`type` match {
      case Error.Permission     => Forbidden(error.toString)
      case Error.InvalidInput   => BadRequest(error.toString)
      case Error.Infrastructure => ServiceUnavailable(error.toString)
      case _ =>                    InternalServerError(error.toString)
    }

  def Unauthorized(msg: String) =
    Response[F](
      status = Status.Unauthorized,
      body = fs2.Stream(msg).through(utf8Encode)
    ).pure[F]

  private val httpRoutesV2: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ GET -> Root / "rates" :? PairQueryParams(pairs) =>

      pairs.fold(err => BadRequest(err.map(_.message).toList.mkString(",")),
        pairs => {
          req.headers.get(CaseInsensitiveString("Authorization")) match {
            case Some(Header(_, value)) =>
              value.trim.split("\\s+") match {
                case Array("Bearer", token) =>
                  EitherT(rates.get(pairs.toGetRatesRequests(token)))
                    .map(rates => Ok(rates.asGetApiResponse))
                    .leftMap(toHttpErrorResponse)
                    .merge.flatten
                case _ => Forbidden("Bearer token required")
              }
            case _ => Unauthorized("No authorization headers")
          }
        })
  }

  val userTokenRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "users" =>
      EitherT(req.as[User].flatMap(user=> auth.registerNewUser(user)))
        .bimap(error => toHttpErrorResponse(error), token => Created(token.asJson)).merge.flatten

    case req @ POST -> Root / "users" / "token" =>
      EitherT(req.as[Token].flatMap(token=> auth.authenticateToken(token)))
        .bimap(error => toHttpErrorResponse(error), _ => Ok("Token is valid")).merge.flatten
  }

  val routes: HttpRoutes[F] = httpRoutesV2 <+> userTokenRoutes

}

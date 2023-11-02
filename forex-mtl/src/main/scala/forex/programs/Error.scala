package forex.programs

import forex.services.Error.{CredentialAuthenticationError, OneFrameCurrencyPairNotFound, OneFrameForbiddenAccess, OneFrameProxyUnsupportedCurrency, OneFrameStaleRates, TokenAuthenticationError}
import forex.services.{Error => RatesServiceError}

sealed trait Error {
  val `type`: Error.ErrorType
  val msg: String
  val reason: Option[String]
  override def toString: String = s"$msg.${reason.map(reason => s" Reason: $reason").getOrElse("")}"
}
object Error {
  sealed trait ErrorType
  case object Permission extends ErrorType
  case object Deserialization extends ErrorType
  case object InvalidInput extends ErrorType
  case object StaleData extends ErrorType
  case object TooManyRequests extends ErrorType
  case object ExistingUser extends ErrorType
  case object InvalidUser extends ErrorType
  case object Infrastructure extends ErrorType
  case object Unexpected extends ErrorType

  final case class RateLookupFailed(`type`: ErrorType, msg: String, reason: Option[String] = None) extends Error

  final case class AuthenticationFailed(`type`: ErrorType, msg: String, reason: Option[String] = None) extends Error

  def toProgramError(error: RatesServiceError): Error =
    (error match {
      case OneFrameForbiddenAccess | TokenAuthenticationError | CredentialAuthenticationError =>
        Error.RateLookupFailed(Permission, "Permission Denied")
      case _: OneFrameProxyUnsupportedCurrency => Error.RateLookupFailed(Deserialization, "Deserialization Error")
      case _: OneFrameCurrencyPairNotFound     => Error.RateLookupFailed(InvalidInput, "Invalid Currency")
      case _: OneFrameStaleRates               => Error.RateLookupFailed(StaleData, "Stale Rate")
      case _                                   => Error.RateLookupFailed(Unexpected, "Unexpected Error")
    }).copy(reason = Some(error.msg))
}

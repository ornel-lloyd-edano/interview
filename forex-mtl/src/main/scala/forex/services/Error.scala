package forex.services

import scala.concurrent.duration.Duration

sealed trait Error {
  val msg: String
}

object Error {
  final case class OneFrameLookupFailed(msg: String) extends Error

  final case class OneFrameProxyUnsupportedCurrency(currencies: Seq[String]) extends Error {
    val msg: String = s"Unsupported currency [${currencies.mkString(",")}] received from One Frame service"
  }

  final case class OneFrameStaleRates(pairs: Seq[String], maxAgeBeforeStale: Duration) extends Error {
    override val msg: String = s"Received rates for [${pairs.mkString(", ")}] but one or more timestamp is $maxAgeBeforeStale ago. Please retry to fetch latest rates."
  }

  final case class OneFrameCurrencyPairNotFound(pairs: Seq[String]) extends Error {
    override val msg: String = s"One or more of these currency pairs [${pairs.mkString(", ")}] are not found in One Frame service."
  }

  case object OneFrameForbiddenAccess extends Error {
    override val msg: String = s"Request to One Frame service was denied. Please check if token is valid."
  }

  case object TokenAuthenticationError extends Error {
    override val msg: String = s"Token claims does not match signature."
  }

  case object CredentialAuthenticationError extends Error {
    override val msg: String = s"Invalid username or password."
  }

  case object UserAlreadyExists extends Error {
    override val msg: String = s"Fail to register existing user."
  }

  case object InvalidUser extends Error {
    override val msg: String = s"Invalid username or password."
  }
}

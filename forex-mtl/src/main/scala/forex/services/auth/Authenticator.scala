package forex.services.auth

import cats.{Applicative}
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherId}
import cats.syntax.functor._
import forex.config.ApplicationConfig
import forex.services.Error
import forex.services.repo.UserRepository
import forex.services.security.Utils

import java.util.Base64

class Authenticator[F[_] : Applicative](userRepository: UserRepository[F], config: ApplicationConfig) extends Algebra[F] {

  private val secret = config.auth.secret
  private val algorithm = config.auth.algorithm

  override def authenticate(token: Token): F[Either[Error, Token]] = {
    val (claim, signatureFromToken) = token.token.split("\\.") match {
      case Array(claim, sig) => (claim, sig)
      case _                 => ("", "")
    }
    val input = new String(Base64.getDecoder.decode(claim)) + secret
    val signatureFromClaim = Utils.hash(input, algorithm)

    val result = if (signatureFromClaim == signatureFromToken) {
      token.asRight[Error]
    } else {
      Error.TokenAuthenticationError.asLeft
    }
    result.pure[F]
  }

  override def authenticate(credentials: Credential): F[Either[Error, Token]] = {
    userRepository.getUser(credentials.username).map {
      case Some(user) if user.password == Utils.hash(credentials.password, algorithm) =>
        val claim = credentials.username + ":" + credentials.password
        val signature = claim + secret
        val base64Token =  Utils.encode64(claim.getBytes) + "." + Utils.hash(signature, algorithm)
        Token(base64Token).asRight[Error]
      case _ =>
        Error.CredentialAuthenticationError.asLeft
    }
  }
}

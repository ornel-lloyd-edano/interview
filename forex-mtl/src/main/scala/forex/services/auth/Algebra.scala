package forex.services.auth

import forex.services.Error

trait Algebra[F[_]] {

  def authenticate(token: Token): F[Either[Error, Token]]

  def authenticate(credentials: Credential): F[Either[Error, Token]]
}

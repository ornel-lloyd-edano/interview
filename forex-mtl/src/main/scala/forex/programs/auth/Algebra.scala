package forex.programs.auth

import forex.services.auth.Token
import forex.programs.Error
import forex.services.repo.User

trait Algebra [F[_]] {
  def registerNewUser(user: User): F[Either[Error, Token]]
  def authenticateToken(token: Token): F[Either[Error, Token]]
}

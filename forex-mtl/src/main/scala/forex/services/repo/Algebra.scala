package forex.services.repo

trait Algebra[F[_]] {
  def registerNewUser(user: User): F[Unit]
  def getUser(username: String): F[Option[User]]
}

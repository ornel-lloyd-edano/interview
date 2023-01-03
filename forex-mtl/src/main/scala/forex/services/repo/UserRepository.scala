package forex.services.repo

import cats.effect.Sync
import forex.config.ApplicationConfig
import forex.services.security.Utils

import scala.collection.mutable

class UserRepository[F[_]: Sync](config: ApplicationConfig) extends Algebra[F] {

  private val alg = config.auth.algorithm

  private val mockDatabase = mutable.Map(
    "Ornel.Edano" -> User("Ornel.Edano", Utils.hash("guess my password", alg)),
    "Martin.Odersky" -> User("Martin.Odersky", Utils.hash("I know your password", alg)),
    "Jonas.Boner" -> User("Jonas.Boner", Utils.hash("Akka is dead", alg))
  )
  override def registerNewUser(user: User): F[Unit] = Sync[F].delay {
    mockDatabase.put(user.username, user.copy(password = Utils.hash(user.password, alg)))
    ()
  }

  override def getUser(username: String): F[Option[User]] = Sync[F].delay {
    mockDatabase.get(username)
  }

}

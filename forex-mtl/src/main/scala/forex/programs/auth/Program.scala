package forex.programs.auth

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.{ catsSyntaxApplicativeId, catsSyntaxEitherId, toFlatMapOps, toFunctorOps }
import forex.programs.Error
import forex.programs.Error.{ AuthenticationFailed }
import forex.services.auth.{ Token, Algebra => AuthAlgebra }
import forex.services.repo.{ User, Algebra => UserRepoAlgebra }

class Program[F[_]: Sync](userRepository: UserRepoAlgebra[F], authenticator: AuthAlgebra[F]) extends Algebra[F] {

  override def registerNewUser(user: User): F[Either[Error, Token]] = {
    for {
      validUser <- EitherT {
                    if (user.username.nonEmpty && user.password.nonEmpty) {
                      user.asRight[Error].pure[F]
                    } else {
                      (AuthenticationFailed(Error.InvalidUser, "Invalid username or password"): Error)
                        .asLeft[User]
                        .pure[F]
                    }
                  }
      _ <- EitherT {
            userRepository.getUser(user.username).flatMap {
              case Some(_) =>
                (AuthenticationFailed(
                  Error.ExistingUser,
                  "Fail to register a new user.",
                  Some("Existing user with same username was found.")
                ): Error).asLeft[Unit].pure[F]
              case None => userRepository.registerNewUser(validUser).map(_.asRight[Error])
            }
          }
      token <- EitherT(authenticator.authenticate(user.toCredential))
                .leftMap[Error](error => AuthenticationFailed(Error.Permission, "Invalid credentials", Some(error.msg)))
    } yield token
  }.value

  override def authenticateToken(token: Token): F[Either[Error, Token]] =
    EitherT(authenticator.authenticate(token))
      .leftMap[Error](error => AuthenticationFailed(Error.Permission, "Invalid token", Some(error.msg)))
      .value
}

object Program {
  def apply[F[_]: Sync](userRepository: UserRepoAlgebra[F], authenticator: AuthAlgebra[F]): Program[F] =
    new Program(userRepository, authenticator)
}

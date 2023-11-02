package forex.services.auth

import cats.effect.IO
import forex.Utils
import forex.config.Config
import forex.services.Error
import forex.services.repo.UserRepository
import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks


class AuthenticatorSpec extends AnyFunSuite with ScalaCheckDrivenPropertyChecks {

  val config = Config.applicationConfig("app")
  val userRepo = new UserRepository[IO](config)
  val authenticator = new Authenticator[IO](userRepo, config)

  val arbitraryString: Gen[String] = Gen.alphaStr

  test("invalid tokens should not pass authentication") {
    forAll(Utils.tokenGenerator) { randomToken=>
      val expectedFail  = authenticator.authenticate(randomToken).unsafeRunSync()
      expectedFail mustBe Left(Error.TokenAuthenticationError)
    }
  }

  test("invalid credentials should not pass authentication") {
    forAll(Utils.credentialGenerator) { randomCredential =>
      val failedAuthentication = authenticator.authenticate(randomCredential).unsafeRunSync()
      failedAuthentication mustBe Left(Error.CredentialAuthenticationError)
    }
  }

  test("credentials from registered users should pass authentication") {
    forAll(Utils.userGenerator) { randomUser =>
      userRepo.registerNewUser(randomUser).unsafeRunSync()
      val result = authenticator.authenticate(randomUser.toCredential).unsafeRunSync()
      result.isRight mustBe true
    }
  }

  test("generated tokens from authenticated credentials should pass token authentication") {
    forAll(Utils.userGenerator) { randomUser =>
      userRepo.registerNewUser(randomUser).unsafeRunSync()
      val generatedToken = authenticator.authenticate(randomUser.toCredential).unsafeRunSync()
      val expectedToken  = authenticator.authenticate(generatedToken.getOrElse(Token("invalid"))).unsafeRunSync()

      generatedToken mustBe expectedToken
    }
  }

}

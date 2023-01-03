package forex.services.repo

import cats.effect.{IO}
import forex.{Utils, services}
import forex.config.Config
//import forex.services.Error
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class UserRepositorySpec extends AnyFunSuite with ScalaCheckDrivenPropertyChecks {

  val config = Config.applicationConfig("app")

  /*test("fail to register existing user") {
    val repository = new UserRepository[IO](config)
    forAll(Utils.userGenerator) { randomUser =>
      repository.registerNewUser(randomUser).unsafeRunSync()
      val expectedExistingUserError = repository.registerNewUser(randomUser).unsafeRunSync()
      expectedExistingUserError.isLeft mustBe true
      expectedExistingUserError mustBe Left(Error.UserAlreadyExists)
    }
  }*/

  test("fail to get unregistered user") {
    val repository = new UserRepository[IO](config)
    forAll(Utils.userGenerator) { randomUser =>
      val expectedNotFound = repository.getUser(randomUser.username).unsafeRunSync()
      expectedNotFound mustBe None
    }
  }

  test("register user and fetch registered user but with hashed password") {
    val repository = new UserRepository[IO](config)
    forAll(Utils.userGenerator) { randomUser =>
      repository.registerNewUser(randomUser).unsafeRunSync()
      val expectedFound = repository.getUser(randomUser.username).unsafeRunSync()
      expectedFound.get.username mustBe randomUser.username
      assert(expectedFound.get.password != randomUser.username)
      expectedFound.get.password mustBe services.security.Utils.hash(randomUser.password, config.auth.algorithm)
    }
  }

}

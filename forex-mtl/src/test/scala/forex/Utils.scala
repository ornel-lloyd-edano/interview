package forex

import forex.services.auth.{ Credential, Token }
import forex.services.repo.User
import org.scalacheck.Gen
import org.scalacheck.Arbitrary.arbitrary

object Utils {

  implicit val tokenGenerator = Gen.alphaNumStr.map(Token)

  implicit val userGenerator = for {
    username <- arbitrary[String].suchThat(!_.isEmpty)
    password <- arbitrary[String].suchThat(!_.isEmpty)
  } yield User(username, password)

  implicit val credentialGenerator = for {
    username <- arbitrary[String].suchThat(!_.isEmpty)
    password <- arbitrary[String].suchThat(!_.isEmpty)
  } yield Credential(username, password)
}

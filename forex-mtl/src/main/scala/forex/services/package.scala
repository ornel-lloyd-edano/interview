package forex

import forex.services.auth.Credential
import forex.services.repo.User

package object services {
  final val RatesServices = rates.Interpreters
  implicit class UserOps(val user: User) extends AnyVal {
    def toCredential = Credential(user.username, user.password)
  }
}

package forex.services.security

import java.security.MessageDigest
import java.util.Base64

object Utils {

  def encode64(arg: Array[Byte]): String =
    Base64.getEncoder.encodeToString(arg)

  def hash(arg: String, algorithm: String): String =
    encode64(MessageDigest.getInstance(algorithm).digest(arg.getBytes))
}

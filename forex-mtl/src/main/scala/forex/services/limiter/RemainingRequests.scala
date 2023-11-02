package forex.services.limiter

import scala.concurrent.duration.Duration

case class RemainingRequests(count: Int, lastSetTsMillis: Long) {
  def isOlderThanResetTime(resetTimeConfig: Duration): Boolean =
    System.currentTimeMillis() - lastSetTsMillis > resetTimeConfig.toMillis
}

package forex.domain

import java.time.{Clock, OffsetDateTime}

case class Timestamp(value: OffsetDateTime) extends AnyVal

object Timestamp {
  def now(implicit clock: Clock): Timestamp =
    Timestamp(OffsetDateTime.now(clock))

}

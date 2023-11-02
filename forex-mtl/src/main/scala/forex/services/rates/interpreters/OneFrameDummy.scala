package forex.services.rates.interpreters

import cats.Applicative
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherId}
import forex.domain.{Price, Rate, Timestamp}
import forex.services.Error
import forex.services.rates.Algebra

import java.time.Clock

class OneFrameDummy[F[_]: Applicative](clock: Clock) extends Algebra[F] {
  override def get(exchangePair: Seq[Rate.Pair]): F[Error Either List[Rate]] = {
    exchangePair.toList.map(p=> Rate(p, Price(100.0), Timestamp.now(clock) )).asRight[Error].pure[F]
  }
}

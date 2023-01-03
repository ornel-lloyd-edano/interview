package forex.services.rates

import forex.domain.Rate

trait Algebra[F[_]] {
  def get(exchangePair: Seq[Rate.Pair]): F[RateServiceResponse]
}

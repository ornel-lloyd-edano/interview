package forex.programs.rates

import forex.domain.Rate
import forex.programs.Error

trait Algebra[F[_]] {
  def get(request: Protocol.GetRatesRequests): F[Error Either List[Rate]]
}

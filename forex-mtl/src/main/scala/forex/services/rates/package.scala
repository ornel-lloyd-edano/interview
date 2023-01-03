package forex.services

import forex.domain.Rate

package object rates {
  type RatesService[F[_]] = Algebra[F]
  type RateServiceResponse = Either[Error, List[Rate]]
}

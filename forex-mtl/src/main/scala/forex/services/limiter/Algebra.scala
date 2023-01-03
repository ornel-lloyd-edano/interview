package forex.services.limiter

import forex.services.auth.Token

trait Algebra [F[_]] {

  def updateAllRequestCount(requestCount: Int): F[Map[Token, Int]]

  def upsertRequestCount(token: Token, requestCount: Int): F[RemainingRequests]

  def reduceRequestCount(token: Token): F[Option[Int]]

  def getRemainingRequests(token: Token): F[Option[RemainingRequests]]
}

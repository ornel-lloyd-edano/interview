package forex.services.limiter.interpreters

import cats.effect.Sync
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxOptionId, toFlatMapOps, toFunctorOps}
import com.google.common.cache.CacheBuilder
import forex.services.auth.Token
import forex.services.limiter.{Algebra, RemainingRequests}
import scalacache.Entry
import scalacache.guava.GuavaCache

import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala

class RequestLimiter[F[_] : Sync]() extends Algebra[F] {
  import scalacache.modes.sync._

  private val cache = {
    val underlying = CacheBuilder.newBuilder()
      .maximumSize(Integer.MAX_VALUE)
      .build[String, Entry[RemainingRequests]]
    GuavaCache(underlying)
  }

  override def updateAllRequestCount(requestCount: Int): F[Map[Token, Int]] = Sync[F].delay {
    val underlyingTokens = cache.underlying.asMap().asScala.map(_._1).toList
    cache.removeAll()
    underlyingTokens.foreach { tokenValue =>
      //cannot go below 0
      val remainingRequests = RemainingRequests(Math.max(requestCount, 0), System.currentTimeMillis())
      cache.put(tokenValue)(remainingRequests)
    }
    underlyingTokens.map(Token(_) -> requestCount).toMap
  }

  override def upsertRequestCount(token: Token, requestCount: Int): F[RemainingRequests] = Sync[F].delay {
    val remainingRequests = RemainingRequests(Math.max(requestCount, 0), System.currentTimeMillis())
    cache.put(token)(remainingRequests)
    remainingRequests
  }

  override def reduceRequestCount(token: Token): F[Option[Int]] = {
    getRemainingRequests(token).flatMap {
      case Some(RemainingRequests(count, _)) if count > 0 =>
        upsertRequestCount(token, count - 1).map(_.count.some)
      case Some(RemainingRequests(0, _)) =>
        Option(0).pure[F] //cannot go below 0
      case _ =>
        Option.empty[Int].pure[F]
    }
  }

  override def getRemainingRequests(token: Token): F[Option[RemainingRequests]] =
    cache.get(token).pure[F]


}

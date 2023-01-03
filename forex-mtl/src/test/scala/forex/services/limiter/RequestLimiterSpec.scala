package forex.services.limiter

import cats.effect.IO
import forex.Utils
import forex.services.limiter.interpreters.RequestLimiter
import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class RequestLimiterSpec extends AnyFunSuite with ScalaCheckDrivenPropertyChecks {

  val requestLimiter = new RequestLimiter[IO]()

  test("fail to get request count of non existing tokens") {
    forAll(Utils.tokenGenerator) { randomToken =>
      val expectedResult = requestLimiter.getRemainingRequests(randomToken).unsafeRunSync()
      expectedResult mustBe None
    }
  }

  test("upsert token and get its available request count") {
    forAll(Utils.tokenGenerator, Gen.choose(1, Integer.MAX_VALUE)) { (randomToken, randomReqCount) =>
      requestLimiter.upsertRequestCount(randomToken, randomReqCount).unsafeRunSync()
      val expectedReqCount = requestLimiter.getRemainingRequests(randomToken).unsafeRunSync().get.count
      randomReqCount mustBe expectedReqCount
    }
  }

  test("reduce request count of token by -1") {
    forAll(Utils.tokenGenerator, Gen.choose(1, Integer.MAX_VALUE)) { (randomToken, randomReqCount) =>
      requestLimiter.upsertRequestCount(randomToken, randomReqCount).unsafeRunSync()
      requestLimiter.reduceRequestCount(randomToken).unsafeRunSync()
      val expectedReqCount = requestLimiter.getRemainingRequests(randomToken).unsafeRunSync().get.count
      (randomReqCount - 1) mustBe expectedReqCount
    }
  }

  test("update request count of all tokens") {
    forAll(Gen.choose(1, Integer.MAX_VALUE)) { randomReqCount =>
      val results = requestLimiter.updateAllRequestCount(randomReqCount).unsafeRunSync().map(_._2)
      results.nonEmpty mustBe true
      results.forall(_ == randomReqCount) mustBe true
    }
  }

  test("fail to reduce request count of token below zero") {
    forAll(Utils.tokenGenerator) { randomToken =>
      requestLimiter.upsertRequestCount(randomToken, 3).unsafeRunSync()
      requestLimiter.reduceRequestCount(randomToken).unsafeRunSync() // 2
      requestLimiter.reduceRequestCount(randomToken).unsafeRunSync() // 1
      requestLimiter.reduceRequestCount(randomToken).unsafeRunSync() // 0
      requestLimiter.reduceRequestCount(randomToken).unsafeRunSync() // -1
      requestLimiter.reduceRequestCount(randomToken).unsafeRunSync() // -2
      val expectedReqCount = requestLimiter.getRemainingRequests(randomToken).unsafeRunSync().get.count
      expectedReqCount mustBe 0
    }
  }

  test("fail to set token request count below 0") {
    forAll(Utils.tokenGenerator, Gen.choose(Integer.MIN_VALUE, -1)) { (randomToken, randomReqCount) =>
      requestLimiter.upsertRequestCount(randomToken, randomReqCount).unsafeRunSync()
      val expectedReqCount = requestLimiter.getRemainingRequests(randomToken).unsafeRunSync().get.count
      randomReqCount < 0 mustBe true
      expectedReqCount mustBe 0
    }
  }

}

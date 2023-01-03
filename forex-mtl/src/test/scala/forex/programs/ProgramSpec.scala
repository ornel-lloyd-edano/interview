package forex.programs

import cats.effect.IO
import cats.implicits.{catsSyntaxApplicativeId}
import com.google.common.cache.CacheBuilder
import forex.config.Config
import forex.domain.{Currency, Rate}
import forex.programs.Error.RateLookupFailed
import forex.programs.rates.Program
import forex.programs.rates.Protocol.GetRatesRequests
import forex.services
import forex.services.Error.{OneFrameCurrencyPairNotFound, OneFrameStaleRates}
import forex.services.auth.{Authenticator, Token}
import forex.services.limiter.interpreters.RequestLimiter
import forex.services.repo.{User, UserRepository}
import org.scalamock.scalatest.MockFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scalacache.guava.GuavaCache
import scalacache.Entry

import java.time.OffsetDateTime
import scala.concurrent.duration.DurationInt

class ProgramSpec extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with MockFactory {
  val config = Config.applicationConfig("app")
  val requestLimiter = new RequestLimiter[IO]()
  val userRepo = new UserRepository[IO](config)
  val authenticator = new Authenticator[IO](userRepo, config)
  val underlyingCache = CacheBuilder.newBuilder()
    .maximumSize(Integer.MAX_VALUE)
    .build[String, Entry[Either[Error, List[Rate]]]]
  val cache = GuavaCache(underlyingCache)
  val ratesService = mock[services.rates.Algebra[IO]]

  val program = new Program[IO](
    ratesService,
    requestLimiter,
    authenticator,
    cache,
    config
  )

  test("fail to get forex rate if using invalid token") {

    val req = GetRatesRequests(token = "random value", from = Currency.USD, to = Currency.SGD)
    val expectedFail = program.get(req).unsafeRunSync()

    expectedFail.isLeft mustBe true
    expectedFail mustBe Left(RateLookupFailed(Error.Permission, "Permission Denied", Some("Token claims does not match signature.")))
  }

  test("get forex rate if using token from registered user") {
    val newUser = User("Elon Musk", "Space-X")
    userRepo.registerNewUser(newUser).unsafeRunSync()
    val token = authenticator.authenticate(newUser.toCredential).unsafeRunSync()

    val mockRates = List(Rate(from = Currency.USD, to = Currency.SGD, price = 1.37, timestamp = OffsetDateTime.now() ))
    (ratesService.get (_: Seq[Rate.Pair]))
      .expects(Seq(Rate.Pair(from = Currency.USD, to = Currency.SGD)))
      .returns(Right(mockRates).pure[IO])

    val req = GetRatesRequests(token = token.getOrElse(Token("")).token, from = Currency.USD, to = Currency.SGD)
    val expectedRates = program.get(req).unsafeRunSync()

    expectedRates.isRight mustBe true
    expectedRates mustBe Right(mockRates)
  }

  test("fail to get a stale forex rate") {
    val newUser = User("Jack Ma", "Alibaba")
    userRepo.registerNewUser(newUser).unsafeRunSync()
    val token = authenticator.authenticate(newUser.toCredential).unsafeRunSync()

    val error = OneFrameStaleRates(Seq(s"from=${Currency.USD} to=${Currency.JPY}"), config.maxRatesAgeBeforeStale)

    (ratesService.get (_: Seq[Rate.Pair]))
      .expects(Seq(Rate.Pair(from = Currency.USD, to = Currency.JPY)))
      .returns(Left(error).pure[IO])

    val req = GetRatesRequests(token = token.getOrElse(Token("")).token, from = Currency.USD, to = Currency.JPY)
    val expectedFail = program.get(req).unsafeRunSync()

    expectedFail.isLeft mustBe true
    expectedFail mustBe Left(RateLookupFailed(Error.StaleData, "Stale Rate", Some("Received rates for [from=USD to=JPY] but one or more timestamp is 5 minutes ago. Please retry to fetch latest rates.")))
  }

  test("fail to get exchange rate for unsupported currencies") {
    val newUser = User("Elon Musk", "Space-X")
    userRepo.registerNewUser(newUser).unsafeRunSync()
    val token = authenticator.authenticate(newUser.toCredential).unsafeRunSync()

    val error = OneFrameCurrencyPairNotFound(Seq(s"from=${Currency.CHF} to=${Currency.NZD}"))

    (ratesService.get (_: Seq[Rate.Pair]))
      .expects(Seq(Rate.Pair(from = Currency.CHF, to = Currency.NZD)))
      .returns(Left(error).pure[IO])

    val req = GetRatesRequests(token = token.getOrElse(Token("")).token, from = Currency.CHF, to = Currency.NZD)
    val expectedFail = program.get(req).unsafeRunSync()

    expectedFail.isLeft mustBe true
    expectedFail mustBe Left(RateLookupFailed(Error.InvalidInput, "Invalid Currency", Some("One or more of these currency pairs [from=CHF to=NZD] are not found in One Frame service.")))
  }

  test("fail to request more than request-limit.max defined in config per token") {
    val newUser = User("Bill Gates", "Microsoft")
    userRepo.registerNewUser(newUser).unsafeRunSync()
    val token = authenticator.authenticate(newUser.toCredential).unsafeRunSync()

    val mockRates = List(Rate(from = Currency.SGD, to = Currency.AUD, price = 1.15, timestamp = OffsetDateTime.now() ))
    (ratesService.get (_: Seq[Rate.Pair]))
      .expects(Seq(Rate.Pair(from = Currency.SGD, to = Currency.AUD)))
      .returns(Right(mockRates).pure[IO])

    val req = GetRatesRequests(token = token.getOrElse(Token("")).token, from = Currency.SGD, to = Currency.AUD)
    val result1 = program.get(req).unsafeRunSync()
    val result2 = program.get(req).unsafeRunSync()
    val result3 = program.get(req).unsafeRunSync()
    val expectedFail = program.get(req).unsafeRunSync()

    config.requestLimit.max mustBe 3
    result1.isRight mustBe true
    result2.isRight mustBe true
    result3.isRight mustBe true
    expectedFail.isLeft mustBe true
    expectedFail mustBe Left(RateLookupFailed(Error.TooManyRequests,
      "Request limit reached",
      Some("Token has been used for [3] successful requests. " +
        "Use a different token or wait after [4 seconds] to auto-reset the count.")))
  }

  test("resume requests for token when reset time defined in config has been reached") {
    val newUser = User("Mark Zuckerberg", "Meta")
    userRepo.registerNewUser(newUser).unsafeRunSync()
    val token = authenticator.authenticate(newUser.toCredential).unsafeRunSync()

    val req = GetRatesRequests(token = token.getOrElse(Token("")).token, from = Currency.SGD, to = Currency.AUD)
    val result1 = program.get(req).unsafeRunSync()
    val result2 = program.get(req).unsafeRunSync()
    val result3 = program.get(req).unsafeRunSync()
    val expectedFail = program.get(req).unsafeRunSync()
    Thread.sleep(config.requestLimit.resetTime.plus(1.second).toMillis)
    val expectedSuccess = program.get(req).unsafeRunSync()

    config.requestLimit.max mustBe 3
    config.requestLimit.resetTime mustBe 4.seconds
    result1.isRight mustBe true
    result2.isRight mustBe true
    result3.isRight mustBe true
    expectedFail.isLeft mustBe true
    expectedFail mustBe Left(RateLookupFailed(Error.TooManyRequests,
      "Request limit reached",
      Some("Token has been used for [3] successful requests. " +
        "Use a different token or wait after [4 seconds] to auto-reset the count.")))
    expectedSuccess.isRight mustBe true
  }


}

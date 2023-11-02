package forex.config

import scala.concurrent.duration.FiniteDuration

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

case class OneFrameServiceConfig(
  uri: String,
  timeout: FiniteDuration,
  authToken: String
)

case class Auth(algorithm: String, secret: String)

case class RequestLimit(max: Int, resetTime: FiniteDuration, isPerToken: Boolean)

case class ApplicationConfig(
    http: HttpConfig,
    oneFrameService: OneFrameServiceConfig,
    auth: Auth,
    requestLimit: RequestLimit,
    maxRatesAgeBeforeStale: FiniteDuration,
    cachedErrorsTtl: FiniteDuration
)

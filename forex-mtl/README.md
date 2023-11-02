# README
___
## Requirements
### Forex is a simple application that acts as a local proxy for getting exchange rates.

### 1. The service returns an exchange rate when provided with 2 supported currencies
### 2. The rate should not be older than 5 minutes
### 3. The service should support at least 10,000 successful requests per day with 1 API token. Please take note that the drawback of One-Frame service is it supports only a maximum of 1000 requests per day for any given authentication token.
___
## Solution:

### 1. The service returns an exchange rate when provided with 2 supported currencies
#### Used http4s client to send a GET request to One-Frame API. The uri is formed by host:port/rates where host and port are set in config. The uri is added with a header 'token' whose value is set also in configuration but can be overriden by env variable. Finally, 'pair' query params are added to the URI. As One-Frame API always responds in Status 200 regardless if it is a failure or success, it was necessary to parse the content of the body to convert the response to an Either[Error, List[Rate]] 
#### Exposed a route /rates which accept a single pair of 'from' and 'to' query parameters for setting the 3-letter ISO Currency Code (ex. /rates?from=USD&to=EUR). In addition, an alternative route under /v2/rates accepts any number of 'from_to' query params which can accept the two ISO currency codes joined by underscore (ex. /v2/rates?from_to=USD_EUR&from_to=CAD_JPY)

### 2. The rate should not be older than 5 minutes
#### Any rates coming from One-Frame API with timestamps older than 5 minutes are rejected. Forex service responds with a Status 500 and a message indicating to try the request again because it received stale data from the currency rates provider.
#### In addition, cached rates older than 5 minutes are automatically expired.
### 3. The service should support at least 10,000 successful requests per day with 1 API token. (One-Frame API limit is 1000 per day)
#### Requests and responses are cached in Forex Service, the currency pair is the key and the response from One-Frame, whether it is failed or success. is the value.  Rates are cached up to 5 minutes, but some errors are cached for several hours long. By doing so, it will greatly reduce the amount of unnecessary requests going to One-Frame API.
#### In addition, by allowing multiple pairs of currencies under /v2/rates, the consumer can batch their requests to optimize the total number of request that are effectively sent.
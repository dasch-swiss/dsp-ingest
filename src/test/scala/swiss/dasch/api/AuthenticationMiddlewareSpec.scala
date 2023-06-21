package swiss.dasch.api

import swiss.dasch.test.SpecConfigurations.jwtConfigLayer
import zio.*
import zio.http.*
import zio.http.endpoint.*
import zio.http.endpoint.EndpointMiddleware.Typed
import zio.test.{ TestAspect, ZIOSpecDefault, assertCompletes, assertTrue }

object AuthenticationMiddlewareSpec extends ZIOSpecDefault {

  private val middleware =
    HttpAppMiddleware.bearerAuthZIO(token => Authenticator.authenticate(token).fold(_ => false, _ => true))

  private val app =
    Endpoint.get("hello").out[String].implement(_ => ZIO.succeed("test")).toApp @@ middleware

  val request = Request.get(URL(Root / "hello"))

  val spec = suite("AuthenticationMiddlewareSpec")(
    test("valid token should be accepted") {
      for {
        token    <- SpecJwtTokens.validToken()
        _        <- Authenticator.authenticate(token)
        response <- app.runZIO(request.updateHeaders(_.addHeader(Header.Authorization.Bearer(token))))
      } yield assertTrue(response.status == Status.Ok)
    },
    test("request without auth header should be unauthorized") {
      for {
        response <- app.runZIO(request)
      } yield assertTrue(response.status == Status.Unauthorized)
    },
    test("request with invalid token should be unauthorized") {
      for {
        token    <- SpecJwtTokens.tokenWithInvalidSignature()
        response <- app.runZIO(request.updateHeaders(_.addHeader(Header.Authorization.Bearer(token))))
      } yield assertTrue(response.status == Status.Unauthorized)
    },
  ).provide(jwtConfigLayer, AuthenticatorLive.layer) @@ TestAspect.withLiveClock
}

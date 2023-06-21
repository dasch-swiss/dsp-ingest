/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import pdi.jwt.*
import pdi.jwt.exceptions.JwtException

import swiss.dasch.config.Configuration.{ DspApiConfig, JwtConfig }
import zio.*
import zio.json.ast.Json
import zio.prelude.{ Validation, ZValidation }
import zio.test.{ TestAspect, ZIOSpecDefault, assertCompletes, assertTrue }

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.util.Try

object AuthenticatorLiveSpec extends ZIOSpecDefault {

  val spec = suite("AuthenticatorLive")(
    test("A valid token should be verified") {
      for {
        token <- validToken()
        json  <- Authenticator.authenticate(token)
      } yield assertTrue(token.nonEmpty, json != null)
    },
    test("An expired token should fail with a JwtProblem") {
      for {
        expiration <- Clock.instant.map(_.minusSeconds(3600))
        token      <- expiredToken(expiration)
        result     <- Authenticator.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(
          NonEmptyChunk(JwtProblem(s"The token is expired since ${expiration.truncatedTo(ChronoUnit.SECONDS)}"))
        )
      )
    },
    test("An invalid token should fail with JwtProblem") {
      for {
        result <- Authenticator.authenticate("invalid-token").exit
      } yield assertTrue(
        result == Exit.fail(
          NonEmptyChunk(
            JwtProblem("Expected token [invalid-token] to be composed of 2 or 3 parts separated by dots.")
          )
        )
      )
    },
    test("A token with invalid signature should fail with JwtProblem") {
      for {
        token  <- tokenWithInvalidSignature()
        result <- Authenticator.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(NonEmptyChunk(JwtProblem("Invalid signature for this token or wrong algorithm.")))
      )
    },
    test("A token with invalid audience should fail with JwtProblem") {
      for {
        token  <- tokenWithInvalidAudience()
        result <- Authenticator.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(
          NonEmptyChunk(InvalidAudience("Invalid audience: expected https://expected-audience.example.com"))
        )
      )
    },
    test("A token with invalid issuer should fail with JwtProblem") {
      for {
        token  <- tokenWithInvalidIssuer()
        result <- Authenticator.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(NonEmptyChunk(InvalidIssuer("Invalid issuer: expected https://admin.swiss.dasch")))
      )
    },
  ).provide(jwtConfigSpecLayer, AuthenticatorLive.layer) @@ TestAspect.withLiveClock

  private def validToken() =
    ZIO.serviceWithZIO[JwtConfig](token(_))

  private def expiredToken(expiration: Instant) =
    ZIO.serviceWithZIO[JwtConfig](c => token(c, expiration = Some(expiration)))
  private def tokenWithInvalidSignature()       =
    ZIO.serviceWithZIO[JwtConfig](c => token(c, secret = Some("aDifferentKey")))

  private def tokenWithInvalidAudience() =
    ZIO.serviceWithZIO[JwtConfig](c => token(c, audience = Some(Set("invalid-audience"))))

  private def tokenWithInvalidIssuer() =
    ZIO.serviceWithZIO[JwtConfig](c => token(c, issuer = Some("invalid-issuer")))
  private def token(
      jwtConfig: JwtConfig,
      issuer: Option[String] = None,
      subject: Option[String] = None,
      audience: Option[Set[String]] = None,
      expiration: Option[Instant] = None,
      secret: Option[String] = None,
    ) = for {
    now       <- Clock.instant
    jwtConfig <- ZIO.service[JwtConfig]
    claim      = JwtClaim(
                   issuer = issuer.orElse(Some(jwtConfig.issuer)),
                   subject = subject.orElse(Some("some-subject")),
                   audience = audience.orElse(Some(Set(jwtConfig.audience))),
                   issuedAt = Some(now.getEpochSecond),
                   expiration = expiration.orElse(Some(now.plusSeconds(3600))).map(_.getEpochSecond),
                 )
  } yield JwtZIOJson.encode(claim, secret.getOrElse(jwtConfig.secret), JwtAlgorithm.HS256)

  def jwtConfigSpecLayer =
    ZLayer.succeed(JwtConfig("secret-key", "https://expected-audience.example.com", "https://admin.swiss.dasch"))
}

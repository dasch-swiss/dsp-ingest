package swiss.dasch.api
import pdi.jwt.*
import pdi.jwt.exceptions.JwtException
import swiss.dasch.api.AuthenticationMiddleWareSpec.Authentication.{
  AuthenticationError,
  InvalidAudience,
  InvalidIssuer,
  JwtProblem,
  alg,
  verifyToken,
}
import swiss.dasch.config.Configuration.{ DspApiConfig, JwtConfig }
import zio.*
import zio.json.ast.Json
import zio.prelude.{ Validation, ZValidation }
import zio.test.{ TestAspect, ZIOSpecDefault, assertCompletes, assertTrue }

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.util.Try

object AuthenticationMiddleWareSpec extends ZIOSpecDefault {
  object Authentication {
    private val alg = JwtAlgorithm.HS256

    sealed trait AuthenticationError
    case class JwtProblem(message: String) extends AuthenticationError
    case object InvalidAudience            extends AuthenticationError
    case object InvalidIssuer              extends AuthenticationError

    def verifyToken(token: String): ZIO[JwtConfig, NonEmptyChunk[AuthenticationError], JwtClaim] =
      for {
        jwtConfig <- ZIO.service[JwtConfig]
        claims    <- ZIO
                       .fromTry(JwtZIOJson.decode(token, jwtConfig.secret, Seq(alg)))
                       .refineOrDie {
                         case e: JwtException => NonEmptyChunk(JwtProblem(e.getMessage))
                       }
        _         <- verifyClaims(claims, jwtConfig)
      } yield claims

    private def verifyClaims(claims: JwtClaim, jwtConfig: JwtConfig): IO[NonEmptyChunk[AuthenticationError], JwtClaim] =
      val audVal = if (claims.audience.getOrElse(Set.empty).contains(jwtConfig.audience)) { Validation.succeed(claims) }
      else { Validation.fail(InvalidAudience) }

      val issVal = if (claims.issuer.contains(jwtConfig.issuer)) { Validation.succeed(claims) }
      else { Validation.fail(InvalidIssuer) }

      ZIO.fromEither(Validation.validateWith(issVal, audVal)((_, _) => claims).toEither)
  }

  val spec = suite("AuthenticationMiddleWareSpec")(
    test("A valid token should be verified") {
      for {
        token <- validToken()
        json  <- verifyToken(token)
      } yield assertTrue(token.nonEmpty, json != null)
    },
    test("A expired token should fail with a JwtProblem") {
      for {
        expiration <- Clock.instant.map(_.minusSeconds(3600))
        token      <- expiredToken(expiration)
        result     <- verifyToken(token).exit
      } yield assertTrue(
        result == Exit.fail(
          NonEmptyChunk(JwtProblem(s"The token is expired since ${expiration.truncatedTo(ChronoUnit.SECONDS)}"))
        )
      )
    },
    test("An invalid token should fail with JwtProblem") {
      for {
        result <- verifyToken("invalid-token").exit
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
        result <- verifyToken(token).exit
      } yield assertTrue(
        result == Exit.fail(NonEmptyChunk(JwtProblem("Invalid signature for this token or wrong algorithm.")))
      )
    },
    test("A token with invalid audience should fail with JwtProblem") {
      for {
        token  <- tokenWithInvalidAudience()
        result <- verifyToken(token).exit
      } yield assertTrue(result == Exit.fail(NonEmptyChunk(InvalidAudience)))
    },
    test("A token with invalid issuer should fail with JwtProblem") {
      for {
        token  <- tokenWithInvalidIssuer()
        result <- verifyToken(token).exit
      } yield assertTrue(result == Exit.fail(NonEmptyChunk(InvalidIssuer)))
    },
  ).provideLayer(jwtConfigSpecLayer) @@ TestAspect.withLiveClock

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

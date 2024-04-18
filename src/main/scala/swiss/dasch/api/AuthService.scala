/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.AuthScope
import pdi.jwt.*
import swiss.dasch.config.Configuration.JwtConfig
import zio.*
import zio.json.*
import zio.prelude.Validation
import swiss.dasch.api.AuthService.JwtContents

trait AuthService {
  def authenticate(jwtToken: String): ZIO[Any, NonEmptyChunk[AuthenticationError], (AuthScope, JwtClaim)]
}

object AuthService {
  def authenticate(token: String): ZIO[AuthService, NonEmptyChunk[AuthenticationError], (AuthScope, JwtClaim)] =
    ZIO.serviceWithZIO[AuthService](_.authenticate(token))

  case class JwtContents(scope: Option[String] = None)

  implicit val decoder: JsonDecoder[JwtContents] = DeriveJsonDecoder.gen[JwtContents]
  implicit val encoder: JsonEncoder[JwtContents] = DeriveJsonEncoder.gen[JwtContents]
}

sealed trait AuthenticationError { def message: String }
object AuthenticationError {
  final case class JwtProblem(message: String)      extends AuthenticationError
  final case class InvalidContents(message: String) extends AuthenticationError
  final case class InvalidAudience(message: String) extends AuthenticationError
  final case class InvalidIssuer(message: String)   extends AuthenticationError
  final case class SubjectMissing(message: String)  extends AuthenticationError
  def jwtProblem(e: Throwable): AuthenticationError = JwtProblem(e.getMessage)
  def invalidContents(error: String): AuthenticationError =
    InvalidContents(s"Invalid contents: $error")
  def invalidAudience(jwtConfig: JwtConfig): AuthenticationError =
    InvalidAudience(s"Invalid audience: expected ${jwtConfig.audience}")
  def invalidIssuer(jwtConfig: JwtConfig): AuthenticationError =
    InvalidIssuer(s"Invalid issuer: expected ${jwtConfig.issuer}")
  def subjectMissing(): AuthenticationError =
    SubjectMissing(s"Subject is missing.")
}

final case class AuthServiceLive(jwtConfig: JwtConfig) extends AuthService {
  private val alg      = Seq(JwtAlgorithm.HS256)
  private val secret   = jwtConfig.secret
  private val audience = jwtConfig.audience
  private val issuer   = jwtConfig.issuer

  def authenticate(jwtString: String): IO[NonEmptyChunk[AuthenticationError], (AuthScope, JwtClaim)] =
    if (jwtConfig.disableAuth) {
      ZIO.succeed((AuthScope(Set(AuthScope.ScopeValue.Admin)), JwtClaim(subject = Some("developer"))))
    } else {
      ZIO
        .fromTry(JwtZIOJson.decode(jwtString, secret, alg))
        .mapError(e => NonEmptyChunk(AuthenticationError.jwtProblem(e)))
        .flatMap(verifyClaim)
    }

  private def verifyClaim(claim: JwtClaim): IO[NonEmptyChunk[AuthenticationError], (AuthScope, JwtClaim)] = {
    val audVal = if (claim.audience.getOrElse(Set.empty).contains(audience)) { Validation.succeed(claim) }
    else { Validation.fail(AuthenticationError.invalidAudience(jwtConfig)) }

    val issVal = if (claim.issuer.contains(issuer)) { Validation.succeed(claim) }
    else { Validation.fail(AuthenticationError.invalidIssuer(jwtConfig)) }

    val subVal = if (claim.subject.isDefined) { Validation.succeed(claim) }
    else { Validation.fail(AuthenticationError.subjectMissing()) }

    val authScope =
      Validation
        .fromEither(
          for {
            contents  <- Right(Some(claim.content).filter(_.nonEmpty))
            parsed    <- contents.map(_.fromJson[JwtContents]).getOrElse(Right(JwtContents()))
            authScope <- parsed.scope.map(AuthScope.parse).getOrElse(Right(AuthScope.Empty))
          } yield authScope,
        )
        .mapError(AuthenticationError.invalidContents)

    ZIO.fromEither(
      Validation
        .validateWith(authScope, issVal, audVal, subVal)(
          (
            authScope,
            _,
            _,
            _,
          ) => (authScope, claim),
        )
        .toEither,
    )
  }
}

object AuthServiceLive {
  val layer =
    ZLayer.fromZIO(ZIO.serviceWithZIO[JwtConfig] { config =>
      ZIO
        .logWarning("Authentication is disabled => Development flag JWT_DISABLE_AUTH set to true.")
        .when(config.disableAuth) *>
        ZIO.succeed(AuthServiceLive(config))
    })
}

/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import pdi.jwt.*
import swiss.dasch.config.Configuration.JwtConfig
import zio.*
import zio.http.{HttpAppMiddleware, RequestHandlerMiddleware}
import zio.prelude.Validation

trait AuthService {
  def authenticate(jwtToken: String): ZIO[Any, NonEmptyChunk[AuthenticationError], JwtClaim]
}
object AuthService {

  val middleware: RequestHandlerMiddleware[Nothing, AuthService, Nothing, Any] =
    HttpAppMiddleware.bearerAuthZIO(token => AuthService.authenticate(token).fold(_ => false, _ => true))

  def authenticate(token: String): ZIO[AuthService, NonEmptyChunk[AuthenticationError], JwtClaim] =
    ZIO.serviceWithZIO[AuthService](_.authenticate(token))
}

sealed trait AuthenticationError { def message: String }
object AuthenticationError {
  final case class JwtProblem(message: String)      extends AuthenticationError
  final case class InvalidAudience(message: String) extends AuthenticationError
  final case class InvalidIssuer(message: String)   extends AuthenticationError
  final case class SubjectMissing(message: String)  extends AuthenticationError
  def jwtProblem(e: Throwable): AuthenticationError = JwtProblem(e.getMessage)
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

  def authenticate(jwtString: String): IO[NonEmptyChunk[AuthenticationError], JwtClaim] =
    if (jwtConfig.disableAuth) { ZIO.succeed(JwtClaim(subject = Some("developer"))) }
    else {
      ZIO
        .fromTry(JwtZIOJson.decode(jwtString, secret, alg))
        .mapError(e => NonEmptyChunk(AuthenticationError.jwtProblem(e)))
        .flatMap(verifyClaim)
    }

  private def verifyClaim(claim: JwtClaim): IO[NonEmptyChunk[AuthenticationError], JwtClaim] = {
    val audVal = if (claim.audience.getOrElse(Set.empty).contains(audience)) { Validation.succeed(claim) }
    else { Validation.fail(AuthenticationError.invalidAudience(jwtConfig)) }

    val issVal = if (claim.issuer.contains(issuer)) { Validation.succeed(claim) }
    else { Validation.fail(AuthenticationError.invalidIssuer(jwtConfig)) }

    val subVal = if (claim.subject.isDefined) { Validation.succeed(claim) }
    else { Validation.fail(AuthenticationError.subjectMissing()) }

    ZIO.fromEither(
      Validation
        .validateWith(issVal, audVal, subVal)(
          (
            _,
            _,
            _
          ) => claim
        )
        .toEither
    )
  }
}

object AuthServiceLive {
  val layer =
    ZLayer.fromZIO(ZIO.serviceWithZIO[JwtConfig] { config =>
      ZIO
        .logWarning("Authentication is disabled => Development flag JWT_DISABLE_AUTH set to true ")
        .when(config.disableAuth) *>
        ZIO.succeed(AuthServiceLive(config))
    })
}
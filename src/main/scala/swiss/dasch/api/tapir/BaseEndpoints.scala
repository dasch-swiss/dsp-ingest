/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.tapir

import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import sttp.tapir.{ Codec, EndpointOutput, PublicEndpoint, oneOf, oneOfVariant, statusCode }
import swiss.dasch.api.tapir.BaseEndpoints.defaultErrorOutputs
import swiss.dasch.api.{ ApiProblem, AuthService }
import zio.ZLayer

case class UserSession(subject: String)

case class BaseEndpoints(authService: AuthService) {

  val publicEndpoint: PublicEndpoint[Unit, ApiProblem, Unit, Any] = endpoint
    .errorOut(defaultErrorOutputs)

  val secureEndpoint: ZPartialServerEndpoint[Any, String, UserSession, Unit, ApiProblem, Unit, Any] = endpoint
    .errorOut(defaultErrorOutputs)
    .securityIn(auth.bearer[String](WWWAuthenticateChallenge.bearer))
    .zServerSecurityLogic[Any, UserSession](handleAuth)

  private def handleAuth(token: String) = authService
    .authenticate(token)
    .map(_.subject)
    .some
    .mapBoth(
      e => ApiProblem.Unauthorized(e.map(_.map(_.message).mkString(", ")).getOrElse("")),
      subject => UserSession(subject),
    )
}

object BaseEndpoints {

  val layer = ZLayer.fromFunction(BaseEndpoints.apply _)

  val defaultErrorOutputs: EndpointOutput.OneOf[ApiProblem, ApiProblem] =
    oneOf[ApiProblem](
      oneOfVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiProblem.BadRequest])),
      oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[ApiProblem.InternalServerError])),
      oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[ApiProblem.NotFound])),
      oneOfVariant(statusCode(StatusCode.ServiceUnavailable).and(jsonBody[ApiProblem.Unhealthy])),
      oneOfVariant(statusCode(StatusCode.Unauthorized).and(jsonBody[ApiProblem.Unauthorized])),
      //    oneOfVariant(statusCode(StatusCode.Forbidden).and(jsonBody[Forbidden])),
      //    oneOfVariant(statusCode(StatusCode.Conflict).and(jsonBody[Conflict])),
      //    oneOfVariant(statusCode(StatusCode.Unauthorized).and(jsonBody[Unauthorized])),
      //    oneOfVariant(statusCode(StatusCode.UnprocessableEntity).and(jsonBody[ValidationFailed])),
    )
}
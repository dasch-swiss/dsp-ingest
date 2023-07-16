/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.MaintenanceActions
import zio.*
import zio.http.Header.{ ContentDisposition, ContentType }
import zio.http.HttpError.*
import zio.http.Path.Segment.Root
import zio.http.codec.HttpCodec.*
import zio.http.codec.*
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint.*
import zio.http.*

object MaintenanceEndpoint {

  val endpoint = Endpoint
    .post("maintenance" / "create-originals" / string("shortcode"))
    .out[String]
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  val route =
    endpoint.implement(shortcode =>
      MaintenanceActions.createOriginals(shortcode).runHead.mapBoth(ApiProblem.internalError, _.toString)
    )

  val app = route.toApp
}

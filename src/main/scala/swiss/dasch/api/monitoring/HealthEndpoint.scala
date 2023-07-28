/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.monitoring

import swiss.dasch.infrastructure
import swiss.dasch.infrastructure.HealthCheckService
import zio.*
import zio.http.*
import zio.json.EncoderOps

object HealthEndpoint {
  val app: HttpApp[HealthCheckService, Nothing] = Http.collectZIO {
    case Method.GET -> Root / "health" =>
      HealthCheckService.check.map { result =>
        val response = Response.json(result.toJson)
        result.status match {
          case infrastructure.Status.UP   => response
          case infrastructure.Status.DOWN => response.withStatus(Status.ServiceUnavailable)
        }
      }
  }
}

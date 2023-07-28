/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.tapir

import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import swiss.dasch.api.ApiProblem
import swiss.dasch.infrastructure.Health
import zio.*

final case class MonitoringEndpoints(base: BaseEndpoints) {

  val infoEndpoint: PublicEndpoint[Unit, ApiProblem, InfoEndpointResponse, Any] =
    base.publicEndpoint.get.in("info").out(jsonBody[InfoEndpointResponse].example(InfoEndpointResponse.instance))

  val healthEndpoint: PublicEndpoint[Unit, ApiProblem, Health, Any] =
    base.publicEndpoint.get.in("health").out(jsonBody[Health].example(Health.up()))

  val metricsEndpoint: PublicEndpoint[Unit, ApiProblem, String, Any] =
    base.publicEndpoint.get.in("metrics").out(stringBody)

  val endpoints = List(infoEndpoint, healthEndpoint, metricsEndpoint)
}
object MonitoringEndpoints {
  val layer = ZLayer.fromFunction(MonitoringEndpoints.apply _)
}

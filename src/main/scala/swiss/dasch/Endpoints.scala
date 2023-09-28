/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.api.tapir.{ MaintenanceEndpointsHandler, MonitoringEndpointsHandler, ProjectsEndpointsHandler }
import swiss.dasch.version.BuildInfo
import zio.{ Task, ZLayer }

final case class Endpoints(
    private val monitoring: MonitoringEndpointsHandler,
    private val projects: ProjectsEndpointsHandler,
    private val maintenance: MaintenanceEndpointsHandler,
  ) {

  val endpoints: List[ZServerEndpoint[Any, sttp.capabilities.zio.ZioStreams]] = {
    val api  = monitoring.endpoints ++ projects.endpoints ++ maintenance.endpoints
    val docs = createDocsEndpoints(api)
    api ++ docs
  }

  private def createDocsEndpoints(apiEndpoints: List[ZServerEndpoint[Any, sttp.capabilities.zio.ZioStreams]])
      : List[ZServerEndpoint[Any, sttp.capabilities.zio.ZioStreams]] =
    SwaggerInterpreter().fromServerEndpoints[Task](apiEndpoints, BuildInfo.name, BuildInfo.version)
}

object Endpoints {
  val layer = ZLayer.fromFunction(Endpoints.apply _)
}

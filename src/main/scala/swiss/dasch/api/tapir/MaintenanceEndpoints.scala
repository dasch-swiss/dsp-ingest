/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.tapir

import sttp.model.StatusCode
import sttp.tapir.json.zio.jsonBody
import swiss.dasch.api.tapir.ProjectsEndpoints.shortcodePathVar
import zio.{ Chunk, ZLayer }
import sttp.tapir.ztapir.*
import swiss.dasch.api.MaintenanceEndpoint.MappingEntry
import sttp.tapir.generic.auto._
final case class MaintenanceEndpoints(base: BaseEndpoints) {

  val applyTopLeftCorrectionEndpoint = base
    .secureEndpoint
    .post
    .in("maintenance" / "apply-top-left-correction" / shortcodePathVar)
    .out(statusCode(StatusCode.Accepted))

  val createOriginalsEndpoint = base
    .secureEndpoint
    .post
    .in("maintenance" / "create-originals" / shortcodePathVar)
    .in(jsonBody[Chunk[MappingEntry]])
    .out(statusCode(StatusCode.Accepted))

  val endpoints = List(applyTopLeftCorrectionEndpoint, createOriginalsEndpoint)
}

object MaintenanceEndpoints {
  val layer = ZLayer.fromFunction(MaintenanceEndpoints.apply _)
}

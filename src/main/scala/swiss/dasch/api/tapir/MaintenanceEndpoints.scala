/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.tapir

import sttp.model.StatusCode
import sttp.tapir.json.zio.jsonBody
import swiss.dasch.api.tapir.ProjectsEndpoints.shortcodePathVar
import zio.{ Chunk, ZLayer }
import sttp.tapir.ztapir.{ statusCode, * }
import sttp.tapir.generic.auto.*
import zio.json.{ DeriveJsonCodec, JsonCodec }
import zio.schema.{ DeriveSchema, Schema }

final case class MappingEntry(internalFilename: String, originalFilename: String)

object MappingEntry {
  given codec: JsonCodec[MappingEntry] = DeriveJsonCodec.gen[MappingEntry]
  given schema: Schema[MappingEntry]   = DeriveSchema.gen[MappingEntry]
}

final case class MaintenanceEndpoints(base: BaseEndpoints) {

  private val maintenance = "maintenance"

  val applyTopLeftCorrectionEndpoint = base
    .secureEndpoint
    .post
    .in(maintenance / "apply-top-left-correction" / shortcodePathVar)
    .out(statusCode(StatusCode.Accepted))

  val needsTopLeftCorrectionEndpoint = base
    .secureEndpoint
    .get
    .in(maintenance / "needs-top-left-correction")
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))

  val createOriginalsEndpoint = base
    .secureEndpoint
    .post
    .in(maintenance / "create-originals" / shortcodePathVar)
    .in(jsonBody[Chunk[MappingEntry]])
    .out(statusCode(StatusCode.Accepted))

  val needsOriginalsEndpoint = base
    .secureEndpoint
    .get
    .in(maintenance / "needs-originals")
    .in(query[Option[Boolean]]("imagesOnly"))
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))

  val endpoints = List(
    applyTopLeftCorrectionEndpoint,
    createOriginalsEndpoint,
    needsTopLeftCorrectionEndpoint,
    needsOriginalsEndpoint,
  )
}

object MaintenanceEndpoints {
  val layer = ZLayer.fromFunction(MaintenanceEndpoints.apply _)
}

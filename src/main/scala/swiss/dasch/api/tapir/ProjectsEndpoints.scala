/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.tapir

import sttp.model.{ HeaderNames, StatusCode }
import sttp.tapir.EndpointInput
import sttp.tapir.codec.refined.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import swiss.dasch.api.ApiProblem
import swiss.dasch.api.ReportEndpoint.AssetCheckResultResponse
import swiss.dasch.api.tapir.ProjectsEndpoints.shortcodePathVar
import swiss.dasch.domain.ProjectShortcode
import zio.json.{ DeriveJsonCodec, JsonCodec }
import zio.schema.{ DeriveSchema, Schema }
import zio.{ Chunk, ZLayer }

final case class ProjectResponse(id: String)

object ProjectResponse {
  def make(shortcode: ProjectShortcode): ProjectResponse = ProjectResponse(shortcode.value)

  given schema: Schema[ProjectResponse]   = DeriveSchema.gen[ProjectResponse]
  given codec: JsonCodec[ProjectResponse] = DeriveJsonCodec.gen[ProjectResponse]
}

final case class ProjectsEndpoints(base: BaseEndpoints) {

  val getProjectsEndpoint = base
    .secureEndpoint
    .get
    .in("projects")
    .out(jsonBody[Chunk[ProjectResponse]])
    .out(header[String](HeaderNames.ContentRange))

  val getProjectByShortcodeEndpoint = base
    .secureEndpoint
    .get
    .in("projects" / shortcodePathVar)
    .out(jsonBody[ProjectResponse])

  val getProjectsChecksumReport = base
    .secureEndpoint
    .get
    .in("projects" / shortcodePathVar / "checksumreport")
    .out(jsonBody[AssetCheckResultResponse])

  val postBulkIngest = base
    .secureEndpoint
    .post
    .in("projects" / shortcodePathVar / "bulk-ingest")
    .out(jsonBody[ProjectResponse].example(ProjectResponse("0001")))
    .out(statusCode(StatusCode.Accepted))

  val endpoints = List(getProjectsEndpoint, getProjectByShortcodeEndpoint, getProjectsChecksumReport, postBulkIngest)
}

object ProjectsEndpoints {

  val shortcodePathVar: EndpointInput.PathCapture[ProjectShortcode] = path[ProjectShortcode]
    .name("shortcode")
    .description("The shortcode of the project must be an exactly 4 characters long hexadecimal string.")
    .example(ProjectShortcode.from("0001").getOrElse(throw Exception("Invalid shortcode")))

  val layer = ZLayer.fromFunction(ProjectsEndpoints.apply _)
}

/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.server.ziohttp.{ ZioHttpInterpreter, ZioHttpServerOptions }
import swiss.dasch.api.tapir.ProjectsEndpointsResponses.ProjectResponse
import swiss.dasch.api.tapir.{ BaseEndpoints, ProjectsEndpoints, ProjectsEndpointsHandler }
import swiss.dasch.domain.*
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.Projects.{ existingProject, nonExistentProject }
import zio.http.{ Body, Request, Root, Status, URL }
import zio.json.*
import zio.test.{ ZIOSpecDefault, assertTrue }
import zio.{ Chunk, ZIO, http }

object ProjectsEndpointSpec extends ZIOSpecDefault {

  private def executeRequest(request: Request) = for {
    app      <- ZIO.serviceWith[ProjectsEndpointsHandler](handler =>
                  ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(handler.endpoints)
                )
    response <- app.runZIO(request).logError
  } yield response

  private val projectExportSuite = {
    def postExport(shortcode: String | ProjectShortcode) = {
      val request = Request
        .post(Body.empty, URL(Root / "projects" / shortcode.toString / "export"))
        .updateHeaders(_.addHeader("Authorization", "Bearer fakeToken"))
      executeRequest(request)
    }
    suite("POST /projects/{shortcode}/export should,")(
      test("given the project does not exist, return 404") {
        for {
          response <- postExport(nonExistentProject)
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("given the project shortcode is invalid, return 400") {
        for {
          response <- postExport("invalid-short-code")
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("given the project is valid, return 200 with correct headers") {
        for {
          response <- postExport(existingProject)
        } yield assertTrue(
          response.status == Status.Ok,
          response
            .headers
            .get("Content-Disposition")
            .contains(s"attachment; filename=export-${existingProject.toString}.zip"),
          response.headers.get("Content-Type").contains("application/zip"),
        )
      },
    )
  }

  val spec = suite("ProjectsEndpoint")(
    projectExportSuite,
    test("GET /projects should list non-empty project in test folders") {
      val req = Request.get(URL(Root / "projects")).addHeader("Authorization", "Bearer fakeToken")
      for {
        response <- executeRequest(req)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == Chunk(ProjectResponse("0001")).toJson,
      )
    },
  ).provide(
    AssetInfoServiceLive.layer,
    AuthServiceLive.layer,
    BaseEndpoints.layer,
    BulkIngestServiceLive.layer,
    FileChecksumServiceLive.layer,
    ImageServiceLive.layer,
    ProjectServiceLive.layer,
    ProjectsEndpoints.layer,
    ProjectsEndpointsHandler.layer,
    ReportServiceLive.layer,
    SipiClientMock.layer,
    SpecConfigurations.ingestConfigLayer,
    SpecConfigurations.jwtConfigDisableAuthLayer,
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer,
  )
}

/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.server.ziohttp.{ ZioHttpInterpreter, ZioHttpServerOptions }
import swiss.dasch.api.tapir.{ BaseEndpoints, ProjectResponse, ProjectsEndpoints, ProjectsEndpointsHandler }
import swiss.dasch.domain.*
import swiss.dasch.test.SpecConfigurations
import zio.http.{ Request, Root, Status, URL }
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

  val spec = suite("ProjectsEndpoint")(
    test("get /projects should list non-empty project in test folders") {
      val req = Request.get(URL(Root / "projects")).addHeader("Authorization", "Bearer fakeToken")
      for {
        response <- executeRequest(req)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == Chunk(ProjectResponse("0001")).toJson,
      )
    }
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

/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}
import swiss.dasch.domain.*
import swiss.dasch.domain.Exif.Image.OrientationValue
import swiss.dasch.infrastructure.CommandExecutorMock
import swiss.dasch.test.SpecConstants.*
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.util.TestUtils
import zio.*
import zio.http.*
import zio.nio.file
import zio.nio.file.Files
import zio.test.*

object MaintenanceEndpointsSpec extends ZIOSpecDefault {

  private def awaitTrue[R, E](awaitThis: ZIO[R, E, Boolean], timeout: Duration = 3.seconds): ZIO[R, E, Boolean] =
    awaitThis.repeatUntil(identity).timeout(timeout).map(_.getOrElse(false))

  private def executeRequest(request: Request) = for {
    app <- ZIO.serviceWith[MaintenanceEndpointsHandler](handler =>
             ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(handler.endpoints),
           )
    response <- app.runZIO(request).logError
  } yield response

  private val needsOriginalsSuite =
    suite("/maintenance/needs-originals should")(
      test("should return 204 and create a report") {
        val request = Request
          .get(URL(Path.root / "maintenance" / "needs-originals"))
          .addHeader(Header.Authorization.name, "Bearer fakeToken")
        for {
          response <- executeRequest(request)
          projects <- loadReport("needsOriginals_images_only.json")
          status    = response.status
        } yield {
          assertTrue(status == Status.Accepted, projects == Chunk("0001"))
        }
      },
      test("should return 204 and create an extended report") {
        val url: URL =
          URL(Path.root / "maintenance" / "needs-originals").addQueryParams(QueryParams(("imagesOnly", Chunk("false"))))
        val request = Request
          .get(url)
          .addHeader(Header.Authorization.name, "Bearer fakeToken")
        for {
          response <- executeRequest(request)
          projects <- loadReport("needsOriginals.json")
          status    = response.status
        } yield {
          assertTrue(status == Status.Accepted, projects == Chunk("0001"))
        }
      },
    ) @@ TestAspect.withLiveClock

  private def loadReport(name: String) =
    StorageService.getTempFolder().flatMap { tmpDir =>
      val report = tmpDir / "reports" / name
      awaitTrue(Files.exists(report)) *> StorageService.loadJsonFile[Chunk[String]](report)
    }

  private val needsTopleftCorrectionSuite =
    suite("/maintenance/needs-top-left-correction should")(
      test("should return 204 and create a report") {
        val request = Request
          .get(URL(Path.root / "maintenance" / "needs-top-left-correction"))
          .addHeader(Header.Authorization.name, "Bearer fakeToken")
        for {
          _        <- SipiClientMock.setOrientation(OrientationValue.Rotate270CW)
          response <- executeRequest(request)
          projects <- loadReport("needsTopLeftCorrection.json")
          status    = response.status
        } yield {
          assertTrue(status == Status.Accepted, projects == Chunk("0001"))
        }
      },
    ) @@ TestAspect.withLiveClock

  val spec = suite("MaintenanceEndpoint")(needsOriginalsSuite, needsTopleftCorrectionSuite)
    .provide(
      AssetInfoServiceLive.layer,
      AuthServiceLive.layer,
      AuthorizationHandlerLive.layer,
      BaseEndpoints.layer,
      CommandExecutorMock.layer,
      FileChecksumServiceLive.layer,
      MaintenanceActionsLive.layer,
      MaintenanceEndpoints.layer,
      MaintenanceEndpointsHandler.layer,
      MimeTypeGuesser.layer,
      MovingImageService.layer,
      OtherFilesService.layer,
      ProjectService.layer,
      ProjectRepositoryLive.layer,
      SipiClientMock.layer,
      SpecConfigurations.jwtConfigDisableAuthLayer,
      SpecConfigurations.storageConfigLayer,
      StillImageService.layer,
      StorageServiceLive.layer,
      TestUtils.testDbLayerWithEmptyDb,
    )
}

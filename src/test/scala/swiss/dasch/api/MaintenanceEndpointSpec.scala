/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.*
import swiss.dasch.test.SpecConstants.Projects.nonExistentProject
import swiss.dasch.test.{SpecConfigurations, SpecConstants}
import zio.http.*
import zio.nio.file
import zio.nio.file.Files
import zio.test.*
import zio.{Task, ZIO, ZLayer}

object MaintenanceEndpointSpec extends ZIOSpecDefault {

  private val createOriginalsSuite: Spec[SipiClient with ProjectService, Option[Response]] = {
    def createOriginalsRequest(shortcode: ProjectShortcode | String) =
      Request.post(Body.empty, URL(Root / "maintenance" / "create-originals" / shortcode.toString))
    suite("/maintenance/create-originals")(
      test("should return 404 for a non-existent project") {
        val request = createOriginalsRequest(nonExistentProject)
        for {
          response <- MaintenanceEndpoint.app.runZIO(request).logError
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("should return 400 for an invalid project shortcode") {
        val request = createOriginalsRequest("invalid-shortcode")
        for {
          response <- MaintenanceEndpoint.app.runZIO(request).logError
        } yield assertTrue(response.status == Status.BadRequest)
      },
    )
  }

  val spec = suite("MaintenanceEndpoint")(createOriginalsSuite)
    .provide(
      AssetInfoServiceLive.layer,
      FileChecksumServiceLive.layer,
      ProjectServiceLive.layer,
      SpecConfigurations.storageConfigLayer,
      StorageServiceLive.layer,
      ZLayer.succeed(SipiClientMock()),
    )
}

final case class SipiClientMock() extends SipiClient {
  override def help(): Task[SipiOutput] = ???

  override def compare(file1: file.Path, file2: file.Path): Task[SipiOutput] = ???

  override def transcodeImageFile(
      fileIn: file.Path,
      fileOut: file.Path,
      outputFormat: SipiImageFormat,
    ): Task[SipiOutput] =
    Files.createFile(fileOut).as(SipiOutput("", ""))
}

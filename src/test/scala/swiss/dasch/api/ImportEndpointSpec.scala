/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ImportEndpointSpec.postImport
import swiss.dasch.config.Configuration.StorageConfig
import zio.test.{ ZIOSpecDefault, assertCompletes, assertTrue }
import swiss.dasch.domain.{ AssetService, AssetServiceLive, ProjectShortcode }
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.existingProject
import swiss.dasch.test.SpecFileUtil.pathFromResource
import zio.{ UIO, URIO, ZIO }
import zio.http.*
import zio.nio.file.Files
object ImportEndpointSpec extends ZIOSpecDefault {

  private val validContentTypeHeaders = Headers(Header.ContentType(MediaType.application.zip))

  private def postImport(
      shortcode: String | ProjectShortcode,
      body: Body,
      headers: Headers,
    ) = {
    val url = URL(Root / "project" / shortcode.toString / "import")
    ImportEndpoint.app.runZIO(Request.post(body, url).updateHeaders(_ => headers))
  }

  val spec = suite("ImportEndpoint")(
    suite("POST on /project/{shortcode}/import should")(
      test("given the shortcode is invalid, return 400")(for {
        response <- postImport("invalid-shortcode", Body.empty, validContentTypeHeaders)
      } yield assertTrue(response.status == Status.BadRequest)),
      test("given the Content-Type header is invalid/not-present, return 400")(
        for {
          responseNoHeader    <- postImport(existingProject, Body.empty, Headers.empty)
          responseWrongHeader <-
            postImport(existingProject, Body.empty, Headers(Header.ContentType(MediaType.application.json)))
        } yield assertTrue(
          responseNoHeader.status == Status.BadRequest,
          responseWrongHeader.status == Status.BadRequest,
        )
      ),
      test("given the Body is empty, return 400")(for {
        response <- postImport("0003", body = Body.empty, headers = validContentTypeHeaders)
      } yield assertTrue(response.status == Status.BadRequest)),
      test("given the Body is a zip, return 200")(
        for {
          storageConfig <- ZIO.service[StorageConfig]
          response      <- postImport(
                             "0003",
                             body = Body.fromFile(pathFromResource("/test-import.zip").toFile),
                             headers = validContentTypeHeaders,
                           )
          importExists  <- Files.isDirectory(storageConfig.assetPath / "0003")
                           && Files.isDirectory(storageConfig.assetPath / "0003" / "fg")
        } yield assertTrue(response.status == Status.Ok, importExists)
      ),
      test("given the Body is not a zip, will return 400") {
        val shortcode = ProjectShortcode.make("0004").toOption.get
        for {
          storageConfig      <- ZIO.service[StorageConfig]
          response           <- postImport(
                                  shortcode.toString,
                                  body = Body.fromFile(pathFromResource("/test-import-invalid.zip.txt").toFile),
                                  headers = validContentTypeHeaders,
                                )
          importDoesNotExist <- validateImportedProjectExists(storageConfig, shortcode).map(!_)
        } yield assertTrue(response.status == Status.BadRequest, importDoesNotExist)
      },
    )
  ).provide(AssetServiceLive.layer, SpecConfigurations.storageConfigLayer)

  private def validateImportedProjectExists(storageConfig: StorageConfig, shortcode: String | ProjectShortcode)
      : UIO[Boolean] =
    val expectedFiles = List("info", "jp2", "jp2.orig").map("FGiLaT4zzuV-CqwbEDFAFeS." + _)
    val projectPath   = storageConfig.assetPath / shortcode.toString
    ZIO.foreach(expectedFiles)(file => Files.exists(projectPath / file)).map(_.forall(identity))
}

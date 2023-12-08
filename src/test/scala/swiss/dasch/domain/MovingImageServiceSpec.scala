/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.infrastructure.CommandExecutorLive
import swiss.dasch.test.SpecConfigurations
import zio.nio.file.Files
import zio.test.*
import zio.test.Assertion.*
import zio.{Exit, ZIO}

object MovingImageServiceSpec extends ZIOSpecDefault {

  private val shortcode = ProjectShortcode.unsafeFrom("0001")
  private final case class OrigRef(original: Original, assetRef: AssetRef)
  private def createOriginalFile(fileExtension: String): ZIO[StorageService, Throwable, OrigRef] = for {
    assetRef    <- AssetRef.makeNew(shortcode)
    assetDir    <- StorageService.getAssetDirectory(assetRef).tap(Files.createDirectories(_))
    originalPath = assetDir / s"${assetRef.id}.$fileExtension.orig"
    _           <- Files.createFile(originalPath)
    original     = Original(OriginalFile.unsafeFrom(originalPath), NonEmptyString.unsafeFrom(s"test.$fileExtension"))
  } yield OrigRef(original, assetRef)

  private val createDerivativeSuite = suite("createDerivative")(
    test("should die for unsupported files") {
      for {
        // given
        c <- createOriginalFile("txt")
        // when
        exit <- MovingImageService.createDerivative(c.original, c.assetRef).exit
        // then
      } yield assert(exit)(diesWithA[IllegalArgumentException])
    },
    test("should create a derivative for supported files") {
      for {
        // given
        c <- createOriginalFile("mp4")
        // when
        derivative <- MovingImageService.createDerivative(c.original, c.assetRef)
        // then
        expectedDerivativePath <- StorageService
                                    .getAssetDirectory(c.assetRef)
                                    .map(_ / s"${c.assetRef.id}.mp4")
        origChecksum  <- FileChecksumService.createSha256Hash(c.original.file.toPath)
        derivChecksum <- FileChecksumService.createSha256Hash(derivative.toPath)
      } yield assertTrue(
        derivative.toPath == expectedDerivativePath,
        origChecksum == derivChecksum // moving image derivative is just a copy
      )
    }
  )

  val spec = suite("MovingImageService")(createDerivativeSuite)
    .provide(
      StorageServiceLive.layer,
      SpecConfigurations.storageConfigLayer,
      MovingImageService.layer,
      CommandExecutorLive.layer,
      SpecConfigurations.sipiConfigLayer
    )
}

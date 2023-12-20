/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.test.SpecConfigurations
import zio.Scope
import zio.nio.file.Files
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object AssetInfoServiceSpec extends ZIOSpecDefault {

  private val testProject = ProjectShortcode.unsafeFrom("0001")
  private val testChecksumOriginal =
    Sha256Hash.unsafeFrom("fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c")
  private val testChecksumDerivative =
    Sha256Hash.unsafeFrom("0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc")

  private def createInfoFile(
    originalFileExt: String,
    derivativeFileExt: String,
    customJsonProps: Option[String] = None
  ) =
    for {
      assetRef      <- AssetRef.makeNew(testProject)
      assetDir      <- StorageService.getAssetDirectory(assetRef).tap(Files.createDirectories(_))
      simpleInfoFile = assetDir / s"${assetRef.id}.info"
      _             <- Files.createFile(simpleInfoFile)
      _ <- Files.writeLines(
             simpleInfoFile,
             List(s"""{
                     |    ${customJsonProps.map(_ + ",").getOrElse("")}
                     |    "internalFilename" : "${assetRef.id}.$derivativeFileExt",
                     |    "originalInternalFilename" : "${assetRef.id}.$originalFileExt.orig",
                     |    "originalFilename" : "test.$originalFileExt",
                     |    "checksumOriginal" : "$testChecksumOriginal",
                     |    "checksumDerivative" : "$testChecksumDerivative"
                     |}
                     |""".stripMargin)
           )
    } yield (assetRef, assetDir)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AssetInfoService")(
      test("parsing a simple file info works") {
        // given
        for {
          refAndDir           <- createInfoFile(originalFileExt = "pdf", derivativeFileExt = "pdf")
          (assetRef, assetDir) = refAndDir
          // when
          actual <- AssetInfoService.findByAssetRef(assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.pdf"),
          actual.original.file == assetDir / s"${assetRef.id}.pdf.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetRef.id}.pdf",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata == OtherMetadata(None, None)
        )
      },
      test("parsing an info file for a moving image with complete metadata info works") {
        // given
        for {
          refAndDir <- createInfoFile(
                         originalFileExt = "mp4",
                         derivativeFileExt = "mp4",
                         customJsonProps = Some("""
                                                  |"width": 640,
                                                  |"height": 480,
                                                  |"fps": 60,
                                                  |"duration": 3.14,
                                                  |"internalMimeType": "video/mp4",
                                                  |"originalMimeType": "video/mp4"
                                                  |""".stripMargin)
                       )
          (assetRef, assetDir) = refAndDir
          // when
          actual <- AssetInfoService.findByAssetRef(assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.mp4"),
          actual.original.file == assetDir / s"${assetRef.id}.mp4.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetRef.id}.mp4",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata ==
            MovingImageMetadata(
              Dimensions.unsafeFrom(640, 480),
              duration = 3.14,
              fps = 60,
              internalMimeType = Some(MimeType.unsafeFrom("video/mp4")),
              originalMimeType = Some(MimeType.unsafeFrom("video/mp4"))
            )
        )
      },
      test("parsing an info file for a still image with complete metadata info works") {
        // given
        for {
          refAndDir <- createInfoFile(
                         originalFileExt = "png",
                         derivativeFileExt = "jpx",
                         customJsonProps = Some("""
                                                  |"width": 640,
                                                  |"height": 480,
                                                  |"internalMimeType": "image/jpx",
                                                  |"originalMimeType": "image/png"
                                                  |""".stripMargin)
                       )
          (assetRef, assetDir) = refAndDir
          // when
          actual <- AssetInfoService.findByAssetRef(assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.png"),
          actual.original.file == assetDir / s"${assetRef.id}.png.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetRef.id}.jpx",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata ==
            StillImageMetadata(
              Dimensions.unsafeFrom(640, 480),
              internalMimeType = Some(MimeType.unsafeFrom("image/jpx")),
              originalMimeType = Some(MimeType.unsafeFrom("image/png"))
            )
        )
      },
      test("parsing an info file for a other file type with complete metadata info works") {
        // given
        for {
          refAndDir <- createInfoFile(
                         originalFileExt = "pdf",
                         derivativeFileExt = "pdf",
                         customJsonProps = Some("""
                                                  |"internalMimeType": "application/pdf",
                                                  |"originalMimeType": "application/pdf"
                                                  |""".stripMargin)
                       )
          (assetRef, assetDir) = refAndDir
          // when
          actual <- AssetInfoService.findByAssetRef(assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.pdf"),
          actual.original.file == assetDir / s"${assetRef.id}.pdf.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetRef.id}.pdf",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata ==
            OtherMetadata(
              internalMimeType = Some(MimeType.unsafeFrom("application/pdf")),
              originalMimeType = Some(MimeType.unsafeFrom("application/pdf"))
            )
        )
      }
    ).provide(AssetInfoServiceLive.layer, StorageServiceLive.layer, SpecConfigurations.storageConfigLayer)
}

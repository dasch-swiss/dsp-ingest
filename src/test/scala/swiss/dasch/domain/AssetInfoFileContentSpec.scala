/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.test.SpecConfigurations
import zio.json.*
import zio.nio.file.Files
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Scope, ZIO}

object AssetInfoFileContentSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AssetInfoFileContent")(
      test("parsing a simple file info works") {
        ZIO.scoped {
          for {
            tempDir       <- StorageService.createTempDirectoryScoped("test")
            simpleInfoFile = tempDir / "simple.info"
            _             <- Files.createFile(simpleInfoFile)
            _ <- Files.writeLines(
                   simpleInfoFile,
                   List(
                     s""" {
                        |    "internalFilename":"FGiLaT4zzuV-CqwbEDFAFeS.jp2",
                        |    "originalInternalFilename":"FGiLaT4zzuV-CqwbEDFAFeS.jp2.orig",
                        |    "originalFilename":"250x250.jp2",
                        |    "checksumOriginal":"fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c",
                        |    "checksumDerivative":"0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc"
                        |}""".stripMargin
                   )
                 )
            actual <- Files
                        .readAllLines(simpleInfoFile)
                        .map(lines => lines.mkString.fromJson[AssetInfoFileContent])
          } yield assertTrue(
            actual.contains(
              AssetInfoFileContent(
                internalFilename = NonEmptyString.unsafeFrom("FGiLaT4zzuV-CqwbEDFAFeS.jp2"),
                originalInternalFilename = NonEmptyString.unsafeFrom("FGiLaT4zzuV-CqwbEDFAFeS.jp2.orig"),
                originalFilename = NonEmptyString.unsafeFrom("250x250.jp2"),
                checksumOriginal =
                  Sha256Hash.unsafeFrom("fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c"),
                checksumDerivative =
                  Sha256Hash.unsafeFrom("0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc")
              )
            )
          )
        }
      },
      test("parsing an info file with moving image metadata info works") {
        ZIO.scoped {
          for {
            tempDir       <- StorageService.createTempDirectoryScoped("test")
            simpleInfoFile = tempDir / "simple.info"
            _             <- Files.createFile(simpleInfoFile)
            _ <- Files.writeLines(
                   simpleInfoFile,
                   List(
                     s""" {
                        |    "internalFilename":"FGiLaT4zzuV-CqwbEDFAFeS.jp2",
                        |    "originalInternalFilename":"FGiLaT4zzuV-CqwbEDFAFeS.jp2.orig",
                        |    "originalFilename":"250x250.jp2",
                        |    "checksumOriginal":"fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c",
                        |    "checksumDerivative":"0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc",
                        |    "width": 640,
                        |    "height": 480,
                        |    "fps": 60,
                        |    "duration": 3.14
                        |}""".stripMargin
                   )
                 )
            actual <- Files
                        .readAllLines(simpleInfoFile)
                        .map(lines => lines.mkString.fromJson[AssetInfoFileContent])
          } yield assertTrue(
            actual.contains(
              AssetInfoFileContent(
                internalFilename = NonEmptyString.unsafeFrom("FGiLaT4zzuV-CqwbEDFAFeS.jp2"),
                originalInternalFilename = NonEmptyString.unsafeFrom("FGiLaT4zzuV-CqwbEDFAFeS.jp2.orig"),
                originalFilename = NonEmptyString.unsafeFrom("250x250.jp2"),
                checksumOriginal =
                  Sha256Hash.unsafeFrom("fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c"),
                checksumDerivative =
                  Sha256Hash.unsafeFrom("0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc"),
                height = Some(480),
                width = Some(640),
                fps = Some(60),
                duration = Some(3.14)
              )
            )
          )
        }
      }
    ).provide(StorageServiceLive.layer, SpecConfigurations.storageConfigLayer)
}

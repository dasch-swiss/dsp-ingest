/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.test

import org.apache.commons.io.FileUtils
import swiss.dasch.config.Configuration.{IngestConfig, JwtConfig, SipiConfig, StorageConfig}
import zio.nio.file.Files
import zio.{Layer, ULayer, ZIO, ZLayer}

import java.io.IOException

object SpecConfigurations {

  val jwtConfigLayer: ULayer[JwtConfig] =
    ZLayer.succeed(JwtConfig("secret-key", "https://dsp-ingest.dev.dasch.swiss", "https://admin.dev.dasch.swiss"))

  val jwtConfigDisableAuthLayer: ULayer[JwtConfig] =
    ZLayer.succeed(
      JwtConfig("secret-key", "https://dsp-ingest.dev.dasch.swiss", "https://admin.dev.dasch.swiss", disableAuth = true),
    )

  val storageConfigLayer: Layer[IOException, StorageConfig] = ZLayer.scoped {
    for {
      tmpDir  <- Files.createTempDirectoryScoped(None, List.empty)
      assetDir = tmpDir / "asset"
      tempDir  = tmpDir / "temp"
      _       <- Files.createDirectories(assetDir)
      _       <- Files.createDirectories(tempDir)
      _       <- ZIO.attemptBlockingIO(FileUtils.copyDirectory(SpecPaths.testFolder.toFile, assetDir.toFile))
    } yield StorageConfig(assetDir.toString, tempDir.toString)
  }

  val ingestConfigLayer = ZLayer.succeed(IngestConfig(2))

  val sipiConfigLayer = ZLayer.succeed(SipiConfig(useLocalDev = true))
}

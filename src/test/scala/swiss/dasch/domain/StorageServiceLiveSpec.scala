/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecConstants.*
import zio.test.*
import zio.*
import zio.nio.file.Path
object StorageServiceLiveSpec extends ZIOSpecDefault {

  val spec = suite("StorageServiceLiveSpec")(
    test("should return the path of the folder where the asset is stored") {
      for {
        assetPath <- ZIO.serviceWith[StorageConfig](_.assetPath)
        path      <- StorageService.getAssetDirectory(Asset("FGiLaT4zzuV-CqwbEDFAFeS".toAssetId, "0001".toProjectShortcode))
      } yield assertTrue(path == assetPath / "0001" / "fg" / "il")
    },
    test("should return the path of the folder where the asset is stored") {
      assertCompletes
    },
  ).provide(StorageServiceLive.layer, Configuration.layer)
}

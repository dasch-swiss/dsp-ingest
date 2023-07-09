/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain;

import eu.timepit.refined.refineV
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.Assets.existingAsset
import zio.*
import zio.test.*

object AssetsServiceLiveSpec extends ZIOSpecDefault {

  val spec = suite("AssetServiceLive")(
    test("should verify the checksums of an asset's original") {
      for {
        checksumMatches <- AssetService.verifyChecksumOrig(existingAsset)
      } yield assertTrue(checksumMatches)
    },
    test("should verify the checksums of an asset's derivative") {
      for {
        checksumMatches <- AssetService.verifyChecksumDerivative(existingAsset)
      } yield assertTrue(checksumMatches)
    },
    test("should verify the checksums of an asset's derivative and original") {
      for {
        assetInfo       <- StorageService.loadInfoFile(existingAsset)
        checksumMatches <- AssetService.verifyChecksum(assetInfo)
      } yield assertTrue(checksumMatches)
    },
  ).provide(
    AssetServiceLive.layer,
    FileChecksumLive.layer,
    StorageServiceLive.layer,
    SpecConfigurations.storageConfigLayer,
  )
}

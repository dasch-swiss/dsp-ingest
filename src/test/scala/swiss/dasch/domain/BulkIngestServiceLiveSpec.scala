/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.api.SipiClientMock
import swiss.dasch.test.SpecConfigurations
import zio.nio.file.Files
import zio.test.{ZIOSpecDefault, assertTrue}

object BulkIngestServiceLiveSpec extends ZIOSpecDefault {

  private val finalizeBulkIngestSuite = suite("finalize bulk ingest should")(test("remove all files") {
    val shortcode = ProjectShortcode.unsafeFrom("0001")
    for {
      // given
      importDir <- StorageService
                     .getTempDirectory()
                     .map(_ / "import" / shortcode.value)
                     .tap(Files.createDirectories(_))
      _             <- Files.createFile(importDir / "0001.tif")
      mappingCsvFile = importDir.parent.head / s"mapping-$shortcode.csv"
      _             <- Files.createFile(mappingCsvFile)
      // when
      _ <- BulkIngestService.finalizeBulkIngest(shortcode)
      // then
      importDirDeleted   <- Files.exists(importDir).negate
      mappingFileDeleted <- Files.exists(mappingCsvFile).negate
    } yield assertTrue(importDirDeleted && mappingFileDeleted)
  })

  val spec = suite("BulkIngestServiceLive")(
    finalizeBulkIngestSuite
  ).provide(
    AssetInfoServiceLive.layer,
    BulkIngestServiceLive.layer,
    ImageServiceLive.layer,
    IngestService.layer,
    SipiClientMock.layer,
    SpecConfigurations.ingestConfigLayer,
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer
  )
}

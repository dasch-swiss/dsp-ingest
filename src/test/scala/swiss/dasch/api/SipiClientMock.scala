/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.{ SipiClient, SipiImageFormat, SipiOutput }
import zio.nio.file.*
import zio.{ Task, ZIO }

final case class SipiClientMock() extends SipiClient {
  override def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): Task[SipiOutput] = Files.createFile(fileOut).as(SipiOutput("", ""))

  override def applyTopLeftCorrection(fileIn: Path, fileOut: Path): ZIO[SipiClient, Throwable, SipiOutput] =
    Files.createFile(fileOut).as(SipiOutput("", ""))

  override def queryImageFile(file: Path): ZIO[SipiClient, Throwable, SipiOutput] =
    ZIO.succeed(SipiOutput("Exif.Image.Orientation                       0x0112 Short       1  8", ""))
}

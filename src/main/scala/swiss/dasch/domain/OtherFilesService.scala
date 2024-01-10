/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.DerivativeFile.OtherDerivativeFile
import zio.{UIO, ZIO, ZLayer}

final case class OtherFilesService(mimeTypeGuesser: MimeTypeGuesser) {
  def extractMetadata(original: Original, derivative: OtherDerivativeFile): UIO[OtherMetadata] = {
    val originalMimeType = mimeTypeGuesser.guess(original.originalFilename.value)
    val internalMimeType = mimeTypeGuesser.guess(derivative.toPath)
    ZIO.succeed(OtherMetadata(internalMimeType, originalMimeType))
  }
}

object OtherFilesService {
  val layer = ZLayer.derive[OtherFilesService]
}

/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import zio.*
import zio.nio.file.Path

private val text    = Seq("odd", "rng", "txt", "xml", "xsd", "xsl")
private val tables  = Seq("csv", "xls", "xslx")
private val audio   = Seq("mpeg", "mp3", "wav")
private val office  = Seq("pdf", "doc", "docx", "ppt", "pptx")
private val archive = Seq("zip", "tar", "gz", "z", "tar.gz", "tgz", "gzip", "7z")

/**
 * Enumeration of supported file types.
 * See also https://docs.dasch.swiss/2023.11.02/DSP-API/01-introduction/file-formats/
 *
 * @param extensions the file extensions of the supported file types.
 */
enum SupportedFileType(val extensions: Seq[String]) {
  case StillImage  extends SupportedFileType(SipiImageFormat.allExtensions)
  case MovingImage extends SupportedFileType(Seq("mp4"))
  case Other       extends SupportedFileType(text ++ tables ++ audio ++ office ++ archive)
}

object SupportedFileType {

  def fromPath(path: Path): Option[SupportedFileType] = {
    val fileExtension = FilenameUtils.getExtension(path.filename.toString)
    SupportedFileType.values.find(_.extensions.contains(fileExtension))
  }
}

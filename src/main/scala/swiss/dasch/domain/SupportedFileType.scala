/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import zio.*
import zio.nio.file.Path

private val archiveExt  = Seq("7z", "gz", "gzip", "tar", "tar.gz", "tgz", "z", "zip")
private val audioExt    = Seq("mp3", "wav")
private val documentExt = Seq("doc", "docx", "pdf", "ppt", "pptx", "xls", "xlsx")
private val textExt     = Seq("csv", "txt", "xml", "xsd", "xsl")

enum SupportedFileType(val extensions: Seq[String]) {
  case ImageFileType extends SupportedFileType(SipiImageFormat.allExtensions)
  case VideoFileType extends SupportedFileType(Seq("mp4"))
  case OtherFileType extends SupportedFileType(archiveExt ++ audioExt ++ documentExt ++ textExt)
}

object SupportedFileType {

  def fromPath(path: Path): Option[SupportedFileType] = {
    val fileExtension = FilenameUtils.getExtension(path.filename.toString)
    SupportedFileType.values.find(_.extensions.contains(fileExtension))
  }
}

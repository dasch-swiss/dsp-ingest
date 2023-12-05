/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.nio.file.Path
import zio.test.{Gen, ZIOSpecDefault, assertTrue, check}

object SupportedFileTypeSpec extends ZIOSpecDefault {

  val spec = suite("SupportedFileTypesSpec")(
    test("All valid extensions for Other are supported") {

      val text    = Seq("odd", "rng", "txt", "xml", "xsd", "xsl")
      val tables  = Seq("csv", "xls", "xslx")
      val audio   = Seq("mpeg", "mp3", "wav")
      val office  = Seq("pdf", "doc", "docx", "ppt", "pptx")
      val archive = Seq("zip", "tar", "gz", "z", "tar.gz", "tgz", "gzip", "7z")

      val otherFileTypeExtensions = text ++ tables ++ audio ++ office ++ archive

      check(Gen.fromIterable(otherFileTypeExtensions)) { ext =>
        assertTrue(SupportedFileType.fromPath(Path(s"test.$ext")).contains(SupportedFileType.Other))
      }
    },
    test("All valid extensions for StillImage are supported") {
      val imageExt = Seq("jp2", "jpeg", "jpg", "jpx", "png", "tif", "tiff")
      check(Gen.fromIterable(imageExt)) { ext =>
        assertTrue(SupportedFileType.fromPath(Path(s"test.$ext")).contains(SupportedFileType.StillImage))
      }
    },
    test("All valid extensions for MovingImage are supported") {
      val imageExt = Seq("mp4")
      check(Gen.fromIterable(imageExt)) { ext =>
        assertTrue(SupportedFileType.fromPath(Path(s"test.$ext")).contains(SupportedFileType.MovingImage))
      }
    },
    test("Unknown file extensions are not supported") {
      val sampleUnknown = Seq("iff", "xslt", "odf", "m3u", "mob", "epub")
      check(Gen.fromIterable(sampleUnknown)) { ext =>
        assertTrue(SupportedFileType.fromPath(Path(s"test.$ext")).isEmpty)
      }
    }
  )
}

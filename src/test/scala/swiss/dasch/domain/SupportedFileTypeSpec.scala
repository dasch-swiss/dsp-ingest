/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.nio.file.Path
import zio.test.{Gen, ZIOSpecDefault, assertTrue, check}

object SupportedFileTypeSpec extends ZIOSpecDefault {
  private def withUpperCase(lower: Seq[String]): Seq[String] = lower ++ lower.map(_.toUpperCase)

  val spec = suite("SupportedFileTypesSpec")(
    test("All valid extensions for Other are supported") {

      val archive = Seq("7z", "gz", "gzip", "tar", "tar.gz", "tgz", "z", "zip")
      val audio   = Seq("mp3", "mpeg", "wav")
      val office  = Seq("doc", "docx", "pdf", "ppt", "pptx")
      val tables  = Seq("csv", "xls", "xslx")
      val text    = Seq("odd", "rng", "txt", "xml", "xsd", "xsl")

      val otherFileTypeExtensions = text ++ tables ++ audio ++ office ++ archive
      check(Gen.fromIterable(withUpperCase(otherFileTypeExtensions))) { ext =>
        assertTrue(SupportedFileType.fromPath(Path(s"test.$ext")).contains(SupportedFileType.Other))
      }
    },
    test("All valid extensions for StillImage are supported") {
      val imageExt = Seq("jp2", "jpeg", "jpg", "jpx", "png", "tif", "tiff")
      check(Gen.fromIterable(withUpperCase(imageExt))) { ext =>
        assertTrue(SupportedFileType.fromPath(Path(s"test.$ext")).contains(SupportedFileType.StillImage))
      }
    },
    test("All valid extensions for MovingImage are supported") {
      val imageExt = Seq("mp4")
      check(Gen.fromIterable(withUpperCase(imageExt))) { ext =>
        assertTrue(SupportedFileType.fromPath(Path(s"test.$ext")).contains(SupportedFileType.MovingImage))
      }
    },
    test("Unknown file extensions are not supported") {
      val sampleUnknown = Seq("epub", "iff", "m3u", "mob", "odf", "xslt")
      check(Gen.fromIterable(withUpperCase(sampleUnknown))) { ext =>
        assertTrue(SupportedFileType.fromPath(Path(s"test.$ext")).isEmpty)
      }
    }
  )
}
package swiss.dasch.domain

import zio.nio.file.Path
import zio.test.{Gen, ZIOSpecDefault, assertTrue, check}

object SupportedFileTypeSpec extends ZIOSpecDefault {

  val spec = suite("SupportedFileTypesSpec")(
    test("All valid extensions for Other are supported") {
      val archiveExt              = Seq("7z", "gz", "gzip", "tar", "tar.gz", "tgz", "z", "zip")
      val audioExt                = Seq("mp3", "wav")
      val documentExt             = Seq("doc", "docx", "pdf", "ppt", "pptx", "xls", "xlsx")
      val textExt                 = Seq("csv", "txt", "xml", "xsd", "xsl")
      val otherFileTypeExtensions = archiveExt ++ audioExt ++ documentExt ++ textExt

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
      val unknownExt = Seq("iff", "xslt", "mpg", "mpeg")
      check(Gen.fromIterable(unknownExt)) { ext =>
        assertTrue(SupportedFileType.fromPath(Path(s"test.$ext")).isEmpty)
      }
    }
  )
}

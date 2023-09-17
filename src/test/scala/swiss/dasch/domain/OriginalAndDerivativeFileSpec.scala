package swiss.dasch.domain

import zio.nio.file.Path
import zio.test.{ Gen, ZIOSpecDefault, assertTrue, check }

object OriginalAndDerivativeFileSpec extends ZIOSpecDefault {

  private val derivativeFileSuite = suite("DerivativeFile")(
    test("can be created from a Path which represents a file") {
      val path: Path = Path("/tmp/hello.tiff")
      assertTrue(DerivativeFile.fromPath(path).map(_.toPath).contains(path))
    },
    test("cannot be created if filename is not a valid AssetId") {
      val path: Path = Path("/tmp/Name of this file is not an AssetId.tiff")
      assertTrue(DerivativeFile.fromPath(path).isEmpty)
    },
    test("cannot be created from original file") {
      val path: Path = Path(s"/tmp/hello.orig")
      assertTrue(DerivativeFile.fromPath(path).isEmpty)
    },
    test("cannot be created from directory") {
      val path: Path = Path(s"/tmp/hello/")
      assertTrue(DerivativeFile.fromPath(path).isEmpty)
    },
    test("cannot be created from hidden file") {
      val hiddenFiles = Gen.fromIterable(List(".hello.txt", ".hello.tiff"))
      check(hiddenFiles) { filename =>
        val path: Path = Path(s"/tmp/$filename")
        assertTrue(DerivativeFile.fromPath(path).isEmpty)
      }
    },
  )

  private val originalFileSpec = suite("OriginalFile")(
    test("can be created if Path ends with .orig") {
      val path: Path = Path("/tmp/test.orig")
      assertTrue(OriginalFile.fromPath(path).map(_.toPath).contains(path))
    },
    test("cannot be created if filename is not a valid AssetId") {
      val path: Path = Path("/tmp/Name of this file is not an AssetId.orig")
      assertTrue(OriginalFile.fromPath(path).isEmpty)
    },
    test("cannot be created if file is not an .orig file, e.g. directory or other extension") {
      val invalidFileExtensions = Gen.fromIterable(List(".png", ".orig.tiff", "/", ".txt", ""))
      check(invalidFileExtensions) { extension =>
        val path: Path = Path(s"/tmp/test$extension")
        assertTrue(OriginalFile.fromPath(path).isEmpty)
      }
    },
    test("cannot be created from hidden file") {
      val hiddenFiles = Gen.fromIterable(List(".hello.txt", ".hello.orig", ".hello.tiff.orig"))
      check(hiddenFiles) { filename =>
        val path: Path = Path(s"/tmp/$filename")
        assertTrue(OriginalFile.fromPath(path).isEmpty)
      }
    },
  )

  val spec = suite("OriginalAndDerivativeFileSpec")(derivativeFileSuite, originalFileSpec)
}

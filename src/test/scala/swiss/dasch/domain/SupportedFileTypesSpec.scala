package swiss.dasch.domain

import zio.nio.file.Path
import zio.test.{ZIOSpecDefault, assertTrue}

object SupportedFileTypesSpec extends ZIOSpecDefault {

  val spec = suite("SupportedFileTypesSpec")(test("tar.gz should be Other") {
    assertTrue(SupportedFileTypes.fromPath(Path("test.tar.gz")) == Some(SupportedFileTypes.OtherFileType))
  })
}

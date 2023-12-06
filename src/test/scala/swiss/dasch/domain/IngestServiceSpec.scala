package swiss.dasch.domain

import zio.test.{ZIOSpecDefault, assertCompletes}

object IngestServiceSpec extends ZIOSpecDefault {
  val spec = suite("IngestServiceSpec")(test("should") {
    assertCompletes
  })
}

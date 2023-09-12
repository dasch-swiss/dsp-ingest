package swiss.dasch.infrastructure

import zio.ZIO
import zio.test.{ Gen, ZIOSpecDefault, assertTrue, check }

import java.util.UUID

object Base62Spec extends ZIOSpecDefault {

  private val genUuid = Gen.fromZIO(ZIO.succeed(UUID.randomUUID()))

  val spec = suite("Base62")(
    test("should encode and decode a UUID") {
      check(genUuid) { uuid =>
        val encoded: Base62EncodedUuid = Base62.encode(uuid)
        val decoded: UUID              = Base62.decode(encoded)
        assertTrue(uuid == decoded)
      }
    }
  )
}

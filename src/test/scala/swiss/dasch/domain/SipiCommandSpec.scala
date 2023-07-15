package swiss.dasch.domain

import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecConfigurations
import zio.*
import zio.nio.file.*
import zio.test.*

object SipiCommandSpec extends ZIOSpecDefault {
  private val localDevSuite = suite("SipiCommandLocalDev")(
    test("should assemble help command") {
      for {
        assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
        cmd       <- ZIO.serviceWithZIO[SipiCommand](_.help())
      } yield assertTrue(
        cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --help"
      )
    },
    test("should assemble compare command") {
      for {
        assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
        cmd       <- ZIO.serviceWithZIO[SipiCommand](_.compare(Path("/tmp/example"), Path("/tmp/example2")))
      } yield assertTrue(
        cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --compare /tmp/example /tmp/example2"
      )
    },
  ).provide(SpecConfigurations.storageConfigLayer, ZLayer.fromFunction(SipiCommandLocalDev.apply _))

  private val liveSuite = suite("SipiCommandLive")(
    test("should assemble help command") {
      for {
        cmd <- ZIO.serviceWithZIO[SipiCommand](_.help())
      } yield assertTrue(cmd == s"--help")
    },
    test("should assemble compare command") {
      for {
        cmd <- ZIO.serviceWithZIO[SipiCommand](_.compare(Path("/tmp/example"), Path("/tmp/example2")))
      } yield assertTrue(
        cmd == s"--compare /tmp/example /tmp/example2"
      )
    },
  ).provide(ZLayer.fromFunction(SipiCommandLive.apply _))

  val spec = suite("SipiCommand")(
    localDevSuite,
    liveSuite,
  )
}

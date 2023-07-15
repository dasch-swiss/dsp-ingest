package swiss.dasch.domain

import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.{ SipiConfig, StorageConfig }
import swiss.dasch.test.SpecConfigurations
import zio.*
import zio.nio.file.*
import zio.test.*

object SipiCommandLineSpec extends ZIOSpecDefault {
  private val localDevSuite = suite("SipiCommandLocalDev")(
    test("should assemble help command") {
      for {
        assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
        cmd       <- ZIO.serviceWithZIO[SipiCommandLine](_.help())
      } yield assertTrue(
        cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --help"
      )
    },
    test("should assemble compare command") {
      for {
        assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
        cmd       <- ZIO.serviceWithZIO[SipiCommandLine](_.compare(Path("/tmp/example"), Path("/tmp/example2")))
      } yield assertTrue(
        cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --compare /tmp/example /tmp/example2"
      )
    },
  ).provide(
    ZLayer.succeed(SipiConfig(useLocalDev = true)),
    SpecConfigurations.storageConfigLayer,
    SipiCommandLive.layer,
  )

  private val liveSuite = suite("SipiCommandLive")(
    test("should assemble help command") {
      for {
        cmd <- ZIO.serviceWithZIO[SipiCommandLine](_.help())
      } yield assertTrue(cmd == s"/sipi/sipi --help")
    },
    test("should assemble compare command") {
      for {
        cmd <- ZIO.serviceWithZIO[SipiCommandLine](_.compare(Path("/tmp/example"), Path("/tmp/example2")))
      } yield assertTrue(
        cmd == s"/sipi/sipi --compare /tmp/example /tmp/example2"
      )
    },
  ).provide(
    ZLayer.succeed(SipiConfig(useLocalDev = false)),
    SpecConfigurations.storageConfigLayer,
    SipiCommandLive.layer,
  )

  val spec = suite("SipiCommand")(
    localDevSuite,
    liveSuite,
  )
}

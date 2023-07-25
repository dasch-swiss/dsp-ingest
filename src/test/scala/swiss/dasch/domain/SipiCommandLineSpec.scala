/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.{ SipiConfig, StorageConfig }
import swiss.dasch.test.SpecConfigurations
import zio.*
import zio.nio.file.*
import zio.test.*

object SipiCommandLineSpec extends ZIOSpecDefault {
  private val localDevSuite = suite("SipiCommandLineLive set up for local development")(
    test("should assemble compare command") {
      for {
        assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
        cmd       <- ZIO.serviceWithZIO[SipiCommandLine](_.compare(Path("/tmp/example"), Path("/tmp/example2")))
      } yield assertTrue(
        cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --compare /tmp/example /tmp/example2"
      )
    },
    test("should assemble format command") {
      check(Gen.fromIterable(SipiImageFormat.all)) { format =>
        for {
          assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
          cmd       <- ZIO.serviceWithZIO[SipiCommandLine](
                         _.format(format.toCliString, Path("/tmp/example"), Path("/tmp/example2"))
                       )
        } yield assertTrue(
          cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --format ${format.toCliString} /tmp/example /tmp/example2"
        )
      }
    },
    test("should assemble query command") {
      for {
        assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
        cmd       <- ZIO.serviceWithZIO[SipiCommandLine](_.query(Path("/tmp/example")))
      } yield assertTrue(
        cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --query /tmp/example"
      )
    },
    test("should topleft command") {
      for {
        assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
        cmd       <- ZIO.serviceWithZIO[SipiCommandLine](
                       _.topleft(Path("/tmp/example"), Path("/tmp/example2"))
                     )
      } yield assertTrue(
        cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --topleft /tmp/example /tmp/example2"
      )
    },
  ).provide(
    ZLayer.succeed(SipiConfig(useLocalDev = true)),
    SpecConfigurations.storageConfigLayer,
    SipiCommandLineLive.layer,
  )

  private val liveSuite = suite("SipiCommandLineLive set up with local sipi executable")(
    test("should assemble compare command") {
      for {
        cmd <- ZIO.serviceWithZIO[SipiCommandLine](_.compare(Path("/tmp/example"), Path("/tmp/example2")))
      } yield assertTrue(
        cmd == s"/sipi/sipi --compare /tmp/example /tmp/example2"
      )
    },
    test("should assemble format command") {
      check(Gen.fromIterable(SipiImageFormat.all)) { format =>
        for {
          cmd <- ZIO.serviceWithZIO[SipiCommandLine](
                   _.format(format.toCliString, Path("/tmp/example"), Path("/tmp/example2"))
                 )
        } yield assertTrue(cmd == s"/sipi/sipi --format ${format.toCliString} /tmp/example /tmp/example2")
      }
    },
    test("should assemble query command") {
      for {
        cmd <- ZIO.serviceWithZIO[SipiCommandLine](_.query(Path("/tmp/example")))
      } yield assertTrue(cmd == s"/sipi/sipi --query /tmp/example")
    },
    test("should topleft command") {
      for {
        cmd <- ZIO.serviceWithZIO[SipiCommandLine](_.topleft(Path("/tmp/example"), Path("/tmp/example2")))
      } yield assertTrue(cmd == s"/sipi/sipi --topleft /tmp/example /tmp/example2")
    },
  ).provide(
    ZLayer.succeed(SipiConfig(useLocalDev = false)),
    SpecConfigurations.storageConfigLayer,
    SipiCommandLineLive.layer,
  )

  val spec = suite("SipiCommand")(
    localDevSuite,
    liveSuite,
  )
}

/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.SipiCommand.{ FormatArgument, QueryArgument, TopLeftArgument }
import swiss.dasch.test.SpecConfigurations
import zio.*
import zio.nio.file.*
import zio.test.*

object SipiCommandSpec extends ZIOSpecDefault {

  private val localDevSuite = {
    def prefix(assetPath: Path) =
      s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest"
    suite("SipiCommandL set up for local development")(
      test("should assemble format command") {
        check(Gen.fromIterable(SipiImageFormat.all)) { format =>
          for {
            assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
            cmd       <- FormatArgument(format, Path("/tmp/example"), Path("/tmp/example2")).render(prefix(assetPath))
          } yield assertTrue(
            cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --format ${format.toCliString} /tmp/example /tmp/example2"
          )
        }
      },
      test("should assemble query command") {
        for {
          assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
          cmd       <- QueryArgument(Path("/tmp/example")).render(prefix(assetPath))
        } yield assertTrue(
          cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --query /tmp/example"
        )
      },
      test("should assemble topleft command") {
        for {
          assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
          cmd       <- TopLeftArgument(Path("/tmp/example"), Path("/tmp/example2")).render(prefix(assetPath))
        } yield assertTrue(
          cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --topleft /tmp/example /tmp/example2"
        )
      },
    ).provide(SpecConfigurations.storageConfigLayer)
  }

  private val liveSuite = {
    val prefix = "/sipi/sipi"
    suite("SipiCommandL set up with local sipi executable")(
      test("should assemble format command") {
        check(Gen.fromIterable(SipiImageFormat.all)) { format =>
          for {
            cmd <- FormatArgument(format, Path("/tmp/example"), Path("/tmp/example2")).render(prefix)
          } yield assertTrue(cmd == s"/sipi/sipi --format ${format.toCliString} /tmp/example /tmp/example2")
        }
      },
      test("should assemble query command") {
        for {
          cmd <- QueryArgument(Path("/tmp/example")).render(prefix)
        } yield assertTrue(cmd == s"/sipi/sipi --query /tmp/example")
      },
      test("should topleft command") {
        for {
          cmd <- TopLeftArgument(Path("/tmp/example"), Path("/tmp/example2")).render(prefix)
        } yield assertTrue(cmd == s"/sipi/sipi --topleft /tmp/example /tmp/example2")
      },
    )
  }

  val spec = suite("SipiCommand")(
    localDevSuite,
    liveSuite,
  )
}

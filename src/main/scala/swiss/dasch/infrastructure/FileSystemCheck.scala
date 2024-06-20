/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.config.Configuration.StorageConfig
import zio.nio.file.Files
import zio.{IO, RIO, UIO, URLayer, ZIO, ZLayer}

trait FileSystemCheck {
  def checkExpectedFoldersExist(): UIO[Boolean]
  def smokeTest(): IO[IllegalStateException, Unit]
}
object FileSystemCheck {
  def checkExpectedFoldersExist(): RIO[FileSystemCheck, Boolean] =
    ZIO.serviceWithZIO[FileSystemCheck](_.checkExpectedFoldersExist())
  def smokeTestOrDie(): RIO[FileSystemCheck, Unit] =
    ZIO.serviceWithZIO[FileSystemCheck](_.smokeTest()).orDie
}

final case class FileSystemCheckLive(config: StorageConfig) extends FileSystemCheck {
  override def checkExpectedFoldersExist(): ZIO[Any, Nothing, Boolean] =
    Files.isDirectory(config.assetPath) && Files.isDirectory(config.tempPath)

  override def smokeTest(): IO[IllegalStateException, Unit] = {
    val msg =
      s"Stopping the start up. Asset ${config.assetPath} and temp ${config.tempPath} directories not found."
    ZIO
      .fail(new IllegalStateException(msg))
      .whenZIO(checkExpectedFoldersExist().negate) *>
      ZIO.logInfo(s"Serving from ${config.assetPath} and ${config.tempPath} directories.")
  }
}

object FileSystemCheckLive {
  val layer: URLayer[StorageConfig, FileSystemCheck] = ZLayer.derive[FileSystemCheckLive]
}

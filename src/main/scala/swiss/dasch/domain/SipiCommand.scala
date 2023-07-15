/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import java.io.{ File, IOError, IOException }
import scala.sys.process.Process
import zio.{ Task, URLayer, ZIO, ZIOAppDefault, ZLayer }
import swiss.dasch.config.Configuration.{ SipiConfig, StorageConfig }
import zio.nio.file.Path
import zio.*

/** Provides the list commands available to rub by [[SipiClient]].
  */
trait SipiCommand  {
  val sipiExecutable: String                                 = "/sipi/sipi"
  def help(): UIO[String]                                    = ZIO.succeed("--help")
  def compare(file1: Path, file2: Path): IO[IOError, String] = for {
    abs1 <- file1.toAbsolutePath
    abs2 <- file2.toAbsolutePath
  } yield s"--compare $abs1 $abs2"
}
object SipiCommand {
  def help(): ZIO[SipiCommand, Throwable, String] = ZIO.serviceWithZIO[SipiCommand](_.help())

  val layer: URLayer[SipiConfig with StorageConfig, SipiCommand] = ZLayer.fromZIO {
    for {
      config        <- ZIO.service[SipiConfig]
      storageConfig <- ZIO.service[StorageConfig]
      sipiCommand    = if (config.useLocalDev) SipiCommandLocalDev(storageConfig) else SipiCommandLive()
      _             <- ZIO.logInfo(s"sipi.use-local=${config.useLocalDev}, created ${sipiCommand.getClass.getSimpleName}")
    } yield sipiCommand
  }
}

final case class SipiCommandLocalDev(storageConfig: StorageConfig) extends SipiCommand {
  private val absoluteAssetPath                                       = storageConfig.assetPath.toFile.toPath.toAbsolutePath
  private val dockerPrefix: String                                    =
    s"docker run " +
      s"--entrypoint $sipiExecutable " +
      s"-v $absoluteAssetPath:$absoluteAssetPath " +
      s"daschswiss/knora-sipi:latest "
  private def addPrefix[E](cmd: IO[E, String]): IO[E, String]         = cmd.map(dockerPrefix + _)
  override def help(): UIO[String]                                    = addPrefix(super.help())
  override def compare(file1: Path, file2: Path): IO[IOError, String] = addPrefix(super.compare(file1, file2))
}

final case class SipiCommandLive() extends SipiCommand

trait SipiClient {
  def help(): Task[String]
  def compare(file1: Path, file2: Path): Task[String]
}

object SipiClient {
  def help(): ZIO[SipiClient, Throwable, String]                            =
    ZIO.serviceWithZIO[SipiClient](_.help())
  def compare(file1: Path, file2: Path): ZIO[SipiClient, Throwable, String] =
    ZIO.serviceWithZIO[SipiClient](_.compare(file1, file2))
}

final case class SipiClientLive(sipiCommand: SipiCommand) extends SipiClient {
  override def help(): Task[String]                                     = execute(sipiCommand.help())
  private def execute(commandTask: Task[String]): IO[Throwable, String] = for {
    command <- commandTask
    result  <- ZIO.logInfo(s"Calling $command'.") *> ZIO.attemptBlocking(Process(command).!!)
  } yield result

  override def compare(file1: Path, file2: Path): Task[String] = execute(sipiCommand.compare(file1, file2))
}

object SipiClientLive {
  val layer: URLayer[SipiCommand, SipiClient] = ZLayer.fromFunction(SipiClientLive.apply _)
}

// /sipi/sipi --format png /opt/images/pp.jpg /opt/images/hh.png

// /sipi/sipi -C pp.jpg pp.jpg

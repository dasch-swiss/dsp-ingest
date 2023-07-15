/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration.{ SipiConfig, StorageConfig }
import zio.*
import zio.nio.file.Path

import java.io.{ File, IOError, IOException }
import scala.sys.process.{ Process, ProcessLogger, stringToProcess }

/** Defines the commands that can be executed with Sipi.
  *
  * See https://sipi.io/running/#command-line-options
  */
private trait SipiCommandLine      {
  def help(): UIO[String]                                    = ZIO.succeed("--help")
  def compare(file1: Path, file2: Path): IO[IOError, String] = for {
    abs1 <- file1.toAbsolutePath
    abs2 <- file2.toAbsolutePath
  } yield s"--compare $abs1 $abs2"
}
private object SipiCommandLineLive {
  private val sipiExecutable                          = "/sipi/sipi"
  def help(): ZIO[SipiCommandLine, Throwable, String] = ZIO.serviceWithZIO[SipiCommandLine](_.help())

  val layer: URLayer[SipiConfig with StorageConfig, SipiCommandLine] = ZLayer.fromZIO {
    for {
      config            <- ZIO.service[SipiConfig]
      absoluteAssetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath).orDie
      prefix             = if (config.useLocalDev) {
                             s"docker run " +
                               s"--entrypoint $sipiExecutable " +
                               s"-v $absoluteAssetPath:$absoluteAssetPath " +
                               s"daschswiss/knora-sipi:latest"
                           }
                           else { sipiExecutable }
    } yield SipiCommandLineLive(prefix)
  }
}

final private case class SipiCommandLineLive(prefix: String) extends SipiCommandLine {
  private def addPrefix[E](cmd: IO[E, String]): IO[E, String]         = cmd.map(cmdStr => s"$prefix $cmdStr")
  override def help(): UIO[String]                                    = addPrefix(super.help())
  override def compare(file1: Path, file2: Path): IO[IOError, String] = addPrefix(super.compare(file1, file2))
}

final case class SipiOutput(stdOut: String, stdErr: String)
trait SipiClient  {
  def help(): Task[SipiOutput]
  def compare(file1: Path, file2: Path): Task[SipiOutput]
}
object SipiClient {
  def help(): ZIO[SipiClient, Throwable, SipiOutput]                            =
    ZIO.serviceWithZIO[SipiClient](_.help())
  def compare(file1: Path, file2: Path): ZIO[SipiClient, Throwable, SipiOutput] =
    ZIO.serviceWithZIO[SipiClient](_.compare(file1, file2))
}

final case class SipiClientLive(cmd: SipiCommandLine) extends SipiClient    {
  override def help(): Task[SipiOutput]                                = execute(cmd.help())
  private def execute(commandLineTask: Task[String]): Task[SipiOutput] =
    commandLineTask
      .flatMap { cmd =>
        val logger = new InMemoryProcessLogger
        ZIO.logInfo(s"Calling \n$cmd") *>
          ZIO.attemptBlocking(cmd ! logger).as(logger.getOutput)
      }

  override def compare(file1: Path, file2: Path): Task[SipiOutput] = execute(cmd.compare(file1, file2))
}
final private class InMemoryProcessLogger             extends ProcessLogger {
  private val sbOut                    = new StringBuilder
  private val sbErr                    = new StringBuilder
  override def out(s: => String): Unit = sbOut.append(s)
  override def err(s: => String): Unit = sbErr.append(s)
  override def buffer[T](f: => T): T   = f
  def getOutput: SipiOutput            = SipiOutput(sbOut.toString(), sbErr.toString())
}

object SipiClientLive {
  val layer: ZLayer[SipiConfig with StorageConfig, Nothing, SipiClient] =
    SipiCommandLineLive.layer >>> ZLayer.fromFunction(SipiClientLive.apply _)
}

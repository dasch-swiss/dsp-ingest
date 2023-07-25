/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration.{ SipiConfig, StorageConfig }
import zio.*
import zio.nio.file.Path

import scala.sys.process.{ ProcessLogger, stringToProcess }

/** Defines the commands that can be executed with Sipi.
  *
  * See https://sipi.io/running/#command-line-options
  */
private trait SipiCommandLine {

  private def withAbsolutePath(
      file1: Path,
      file2: Path,
      createCommand: (String, String) => String,
    ): UIO[String] = (for {
    abs1 <- file1.toAbsolutePath
    abs2 <- file2.toAbsolutePath
  } yield createCommand.apply(abs1.toString, abs2.toString)).orDie

  def format(
      outputFormat: String,
      fileIn: Path,
      fileOut: Path,
    ): UIO[String] =
    withAbsolutePath(fileIn, fileOut, (a, b) => s"--format $outputFormat $a $b")

  def query(fileIn: Path): UIO[String] =
    fileIn.toAbsolutePath.orDie.map(abs => s"--query $abs")

  /** Applies the top-left correction to the image. This is an undocumented feature of Sipi.
    * @param fileIn
    *   the image file to be corrected
    * @param fileOut
    *   the corrected image file
    * @return
    *   the command line to be executed
    */
  def topleft(fileIn: Path, fileOut: Path): UIO[String] =
    withAbsolutePath(fileIn, fileOut, (a, b) => s"--topleft $a $b")
}
private object SipiCommandLineLive {
  private val sipiExecutable = "/sipi/sipi"

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

  private def addPrefix[E](cmd: IO[E, String]): IO[E, String] = cmd.map(cmdStr => s"$prefix $cmdStr")

  override def format(
      outputFormat: String,
      fileIn: Path,
      fileOut: Path,
    ): UIO[String] = addPrefix(super.format(outputFormat, fileIn, fileOut))

  override def query(fileIn: Path): UIO[String] = addPrefix(super.query(fileIn))

  override def topleft(fileIn: Path, fileOut: Path): UIO[String] = addPrefix(super.topleft(fileIn, fileOut))
}

/** Defines the output format of the image. Used with the `--format` option.
  *
  * https://sipi.io/running/#command-line-options
  */
sealed trait SipiImageFormat {
  def extension: String
  def toCliString: String                          = extension
  def additionalExtensions: List[String]           = List.empty
  def allExtensions: List[String]                  = additionalExtensions.appended(this.extension)
  def acceptsExtension(extension: String): Boolean = allExtensions.contains(extension.toLowerCase)
}

object SipiImageFormat {
  case object Jpx extends SipiImageFormat {
    override def extension: String                  = "jpx"
    override def additionalExtensions: List[String] = List("jp2")
  }
  case object Jpg extends SipiImageFormat {
    override def extension: String                  = "jpg"
    override def additionalExtensions: List[String] = List("jpeg")
  }
  case object Tif extends SipiImageFormat {
    override def extension: String                  = "tif"
    override def additionalExtensions: List[String] = List("tiff")
  }
  case object Png extends SipiImageFormat {
    override def extension: String = "png"
  }
  val all: List[SipiImageFormat] = List(Jpx, Jpg, Tif, Png)
  val allExtension: List[String]                                = all.flatMap(_.allExtensions)
  def fromExtension(extension: String): Option[SipiImageFormat] =
    List(Jpx, Jpg, Tif, Png).find(_.acceptsExtension(extension))
}

final case class SipiOutput(stdOut: String, stdErr: String)
trait SipiClient {

  def applyTopLeftCorrection(fileIn: Path, fileOut: Path): UIO[SipiOutput]

  def queryImageFile(file: Path): UIO[SipiOutput]

  def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): UIO[SipiOutput]
}

object SipiClient {

  def applyTopLeftCorrection(fileIn: Path, fileOut: Path): RIO[SipiClient, SipiOutput] =
    ZIO.serviceWithZIO[SipiClient](_.applyTopLeftCorrection(fileIn, fileOut))

  def queryImageFile(file: Path): RIO[SipiClient, SipiOutput] =
    ZIO.serviceWithZIO[SipiClient](_.queryImageFile(file))

  def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): RIO[SipiClient, SipiOutput] =
    ZIO.serviceWithZIO[SipiClient](_.transcodeImageFile(fileIn, fileOut, outputFormat))
}

final case class SipiClientLive(cmd: SipiCommandLine) extends SipiClient {

  private def execute(commandLineTask: UIO[String]): UIO[SipiOutput] =
    commandLineTask.flatMap { cmd =>
      val logger = new InMemoryProcessLogger
      ZIO.logDebug(s"Calling \n$cmd") *>
        ZIO.succeed(cmd ! logger).as(logger.getOutput).tap(out => ZIO.logInfo(out.toString))
    }.logError

  override def applyTopLeftCorrection(fileIn: Path, fileOut: Path): UIO[SipiOutput] = execute(
    cmd.topleft(fileIn, fileOut)
  )

  override def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): UIO[SipiOutput] = execute(cmd.format(outputFormat.toCliString, fileIn, fileOut))

  override def queryImageFile(file: Path): UIO[SipiOutput] = execute(cmd.query(file))
}

final private class InMemoryProcessLogger extends ProcessLogger {
  private val sbOut                    = new StringBuilder
  private val sbErr                    = new StringBuilder
  override def out(s: => String): Unit = sbOut.append(s + "\n")
  override def err(s: => String): Unit = sbErr.append(s + "\n")
  override def buffer[T](f: => T): T   = f
  def getOutput: SipiOutput            = SipiOutput(sbOut.toString(), sbErr.toString())
}

object SipiClientLive {
  val layer: ZLayer[SipiConfig with StorageConfig, Nothing, SipiClient] =
    SipiCommandLineLive.layer >>> ZLayer.fromFunction(SipiClientLive.apply _)
}

/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import java.io.{ File, IOException }
import scala.sys.process.Process
import zio.{ Task, URLayer, ZIO, ZIOAppDefault, ZLayer }
import swiss.dasch.config.Configuration.{ SipiConfig, StorageConfig }
import zio.nio.file.Path
import zio.*

/** Provides the list commands available to rub by [[SipiCommandRunnerService]].
  */
trait SipiCommand {
  def help(): String
  def compare(file1: String, file2: String): String
}

case class SipiLocalDev() extends SipiCommand {
  private val runSipi                = SipiInContainer()
// sipi container needs to be started first up docker-compose
  private val dockerImageCmd: String = "docker exec dsp-ingest-sipi-1 /sipi/sipi"
//  private val dockerImageCmd: String = "docker run daschswiss/knora-sipi:latest -exec"
//  private val dockerImageCmd: String = "docker run daschswiss/knora-sipi:latest"

  override def help(): String                                = s"$dockerImageCmd ${runSipi.help()}"
  override def compare(file1: String, file2: String): String = s"$dockerImageCmd ${runSipi.compare(file1, file2)}"
}

case class SipiInContainer() extends SipiCommand {
//case class SipiInContainer(storageConfig: StorageConfig) extends SipiCommand {
  private val sipiPath: String   = "/sipi/sipi"
  private val imagesPath: String = "/opt/images/"

  override def help(): String                                = s"$sipiPath --help"
  override def compare(file1: String, file2: String): String =
    s"$sipiPath --compare $imagesPath$file1 $imagesPath$file2"
//  override def compare(file1: String, file2: String): String = {
//    val assetFile1: Path = storageConfig.assetPath / file1
//    val assetFile2: Path = storageConfig.assetPath / file2
//
//    if (assetFile1.ensuring()) ZIO.succeed() //check the docs and do the same fo the file2 ZIO.succeed().when()
//    else ZIO.fail()
//
//    s"$sipiPath -compare $assetFile1 $assetFile2"
//  }
}

object SipiCommand {
  val layer: URLayer[SipiConfig, SipiCommand] = ZLayer.fromZIO {
    ZIO.serviceWith[SipiConfig] { config =>
      if (config.useLocal) SipiLocalDev()
      else SipiInContainer()
    }
  }
}

/** Enables to run SIPI as binary, so it can process the files using [[SipiCommand]]
  */
trait SipiCommandRunnerService {
  def help(): IO[IOException, String]
  def compare(file1: String, file2: String): IO[IOException, String]
}

object SipiCommandRunnerService {
  def help(): ZIO[SipiCommandRunnerService, IOException, String] =
    ZIO.serviceWithZIO[SipiCommandRunnerService](_.help())

  def compare(file1: String, file2: String): ZIO[SipiCommandRunnerService, IOException, String] =
    ZIO.serviceWithZIO[SipiCommandRunnerService](_.compare(file1, file2))
}

final case class SipiCommandRunnerServiceLive() extends SipiCommandRunnerService {
//final case class ImageHandlerLive(sc: SipiCommand) extends ImageHandler {
  private val runSipi                          = SipiInContainer()
  override def help(): IO[IOException, String] =
    ZIO.logInfo("Command --help executed.") *> ZIO.succeed(Process(runSipi.help()).!!)

  override def compare(file1: String, file2: String): IO[IOException, String] =
    ZIO.logInfo("Command --compare executed.") *> ZIO.succeed(Process(runSipi.compare(file1, file2)).!!)
//  def help(): String                        = Process(sc.help()).!!
//  def compare(file1: String, file2: String): String = Process(sc.compare(file1, file2)).!!
}

object SipiCommandRunnerServiceLive {
  val layer: URLayer[SipiCommand, SipiCommandRunnerService] =
    ZLayer.fromFunction(SipiCommandRunnerServiceLive.apply _)
}

// for local development
//  object ImageHandler extends ZIOAppDefault {
//  val run =
//    ZIO
//      .service[ImageHandler]
////      .tap(runSipi => zio.Console.printLine(runSipi.help()))
//       .tap(runSipi => zio.Console.printLine(runSipi.compare("pp.jpg", "pp.jpg")))
//      .provide(SipiCommand.layer, Configuration.layer, ImageHandlerLive.layer)
//}

// /sipi/sipi --compare /opt/images/pp.jpg /opt/images/pp.jpg
// /sipi/sipi --format png /opt/images/pp.jpg /opt/images/hh.png

// /sipi/sipi -C pp.jpg pp.jpg

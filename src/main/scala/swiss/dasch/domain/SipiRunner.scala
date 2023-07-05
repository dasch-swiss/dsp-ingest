/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import java.io.File
import scala.sys.process.Process
import zio.ZLayer
import zio.ZIO
import swiss.dasch.config.*
import zio.ZIOAppDefault
import swiss.dasch.config.Configuration.{SipiConfig, StorageConfig}
import zio.nio.file.Path

trait SipiCommand {
  def help(): String
  def compare(file1: String, file2: String): String
}

case class SipiLocalDev() extends SipiCommand {
  private val runSipi                = SipiInContainer()
// sipi container needs to be started first up docker-compose
  private val dockerImageCmd: String = "docker exec dsp-ingest-sipi-1 /sipi/sipi"

  override def help(): String                                = s"$dockerImageCmd ${runSipi.help()}"
  override def compare(file1: String, file2: String): String = s"$dockerImageCmd ${runSipi.compare(file1, file2)}"
}

case class SipiInContainer() extends SipiCommand {
  private val sipiPath: String = "/sipi/sipi"
  private val imagesPath: String = "/opt/images/"

  override def help(): String                                = s"$sipiPath --help"
  override def compare(file1: String, file2: String): String = s"$sipiPath --compare $imagesPath$file1 $imagesPath$file2"
}

object SipiCommand {
  val layer: ZLayer[SipiConfig, Nothing, SipiCommand] = ZLayer.fromZIO {
    ZIO.service[SipiConfig].map { config =>
      if (config.useLocal) SipiLocalDev()
      else SipiInContainer()
    }
  }
}

trait ImageHandler {
  def help(): String
  def compare(file1: String, file2: String): String
}
final case class ImageHandlerLive(sc: SipiCommand) extends ImageHandler {
  def help(): String                        = Process(sc.help()).!!
  def compare(file1: String, file2: String): String = Process(sc.compare(file1, file2)).!!
}

object ImageHandlerLive {
  val layer: ZLayer[SipiCommand, Nothing, ImageHandlerLive] =
    ZLayer.fromFunction(ImageHandlerLive.apply _)
}

object ImageHandler extends ZIOAppDefault {
  val run =
    ZIO
      .service[ImageHandler]
//      .tap(runSipi => zio.Console.printLine(runSipi.help()))
       .tap(runSipi => zio.Console.printLine(runSipi.compare("pp.jpg", "pp.jpg")))
      .provide(SipiCommand.layer, Configuration.layer, ImageHandlerLive.layer)
}

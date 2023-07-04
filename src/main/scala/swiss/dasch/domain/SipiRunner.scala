package swiss.dasch.domain

import java.io.File
import scala.sys.process.Process
import zio.ZLayer
import zio.ZIO
import swiss.dasch.config.*
import zio.ZIOAppDefault
import swiss.dasch.config.Configuration.SipiConfig

trait SipiCommand {
  def help(): String
  def compare(file1: String, file2: String): String
}

case class SipiLocalDev() extends SipiCommand {
  private val runSipi                = SipiInContainer()
  private val dockerImageCmd: String = "docker run daschswiss/knora-sipi:latest -exec"

  override def help(): String                                = s"$dockerImageCmd ${runSipi.help()}"
  override def compare(file1: String, file2: String): String = s"$dockerImageCmd ${runSipi.compare(file1, file2)}"
}

case class SipiInContainer() extends SipiCommand {
  private val sipiPath: String = "/sipi/sipi"

  override def help(): String                                = s"$sipiPath --help"
  override def compare(file1: String, file2: String): String = s"$sipiPath --Compare $file1 --Compare $file2"
}

object SipiCommand {
  val layer: ZLayer[SipiConfig, Nothing, SipiCommand] = ZLayer.fromZIO {
    ZIO.service[SipiConfig].map { config =>
      if (config.useLocal) SipiLocalDev()
      else SipiInContainer()
    }
  }
}

trait ImageHandler {}
final case class ImageHandlerLive(sc: SipiCommand) extends ImageHandler {
  def help(): String                        = Process(sc.help()).!!
  def compare(file1: String, file2: String) = Process(sc.compare(file1, file2)).!!
}

object ImageHandlerLive {
  val layer = ZLayer.fromFunction(ImageHandlerLive.apply _)
}

object ImageHandler extends ZIOAppDefault {
  val run =
    ZIO
      .service[ImageHandler]
      .tap(runSipi => println(runSipi.help()))
      // .tap(runSipi => println(runSipi.compare()))
      .provide(SipiCommand.layer, Configuration.layer, ImageHandlerLive.layer)
}

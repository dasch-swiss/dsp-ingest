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
}

case class SipiLocalDev() extends SipiCommand {
  private val runSipi = SipiInContainer()

  override def help(): String = s"docker run daschswiss/knora-sipi:latest -exec ${runSipi.help()}"
}

case class SipiInContainer() extends SipiCommand {
  override def help(): String = "/sipi/sipi --help"
}

object SipiCommand {
  val layer: ZLayer[SipiConfig, Nothing, SipiCommand] = ZLayer.fromZIO {
    ZIO.service[SipiConfig].map { config =>
      if (config.useLocal) SipiLocalDev()
      else SipiInContainer()
    }
  }
}

trait ImageHandler
final case class ImageHandlerLive(sc: SipiCommand) extends ImageHandler {
  def help(): String = Process(sc.help()).!!
}

object ImageHandlerLive {
  val layer = ZLayer.fromFunction(ImageHandlerLive.apply _)
}

object ImageHandler extends ZIOAppDefault {
  val run =
    ZIO
      .service[ImageHandler]
      .tap(runSipi => println(runSipi.help()))
      .provide(SipiCommand.layer, Configuration.layer, ImageHandlerLive.layer)
}

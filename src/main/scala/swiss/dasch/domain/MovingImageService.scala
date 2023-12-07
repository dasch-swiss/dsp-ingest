package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import swiss.dasch.config.Configuration.SipiConfig
import swiss.dasch.domain.DerivativeFile.MovingImageDerivativeFile
import zio.json.{DecoderOps, DeriveJsonDecoder, JsonDecoder}
import zio.nio.file.Path
import zio.{Task, ZIO, ZLayer}

import scala.sys.process.{ProcessLogger, stringToProcess}

final case class MovingImageMetadata(width: Int, height: Int, duration: Double, fps: Int)

case class MovingImageService(storage: StorageService, config: SipiConfig, executor: CommandExecutor) {

  def createDerivative(original: Original, assetRef: AssetRef): Task[MovingImageDerivativeFile] = {
    val fileExtension = FilenameUtils.getExtension(original.originalFilename.toString)
    for {
      _ <- ZIO.unless(SupportedFileType.MovingImage.acceptsExtension(fileExtension))(
             ZIO.die(new IllegalArgumentException(s"File extension $fileExtension is not supported for moving images"))
           )
      assetDir      <- storage.getAssetDirectory(assetRef)
      derivativePath = assetDir / s"${assetRef.id}.$fileExtension"
      derivative     = MovingImageDerivativeFile.unsafeFrom(derivativePath)
      _             <- storage.copyFile(original.file.toPath, derivativePath).as(Asset.makeOther(assetRef, original, derivative))
    } yield derivative
  }

  def extractKeyFrames(file: DerivativeFile, assetRef: AssetRef): Task[Unit] =
    ZIO.logInfo(s"Extracting key frames for $file, $assetRef")

  private val extractCommand = "ffprobe"
  private val extractFlags =
    "-v error -select_streams v:0 -show_entries stream=width,height,duration,r_frame_rate -print_format json -i"

  private def runInDocker(entrypoint: String, params: String, assetPath: Path) =
    s"docker run --entrypoint $entrypoint -v $assetPath:$assetPath daschswiss/knora-sipi:latest $params"

  def extractMetadata(file: DerivativeFile, assetRef: AssetRef): Task[MovingImageMetadata] =
    for {
      assetDir <- storage.getAssetDirectory(assetRef).flatMap(_.parent.head.toAbsolutePath)
      absPath  <- file.toPath.toAbsolutePath
      command = if (config.useLocalDev) { runInDocker(extractCommand, extractFlags + " " + absPath, assetDir) }
                else { s"$extractCommand $extractFlags $absPath" }
      metadata <- executor.execute(command).flatMap(parseMetadata)
    } yield metadata

  final case class FfprobeStream(width: Int, height: Int, duration: Double, r_frame_rate: String)
  object FfprobeStream {
    implicit val decoder: JsonDecoder[FfprobeStream] = DeriveJsonDecoder.gen[FfprobeStream]
  }

  final case class FfprobeOut(streams: Array[FfprobeStream]) {
    def toMetadata: Option[MovingImageMetadata] =
      streams.headOption.map { stream =>
        MovingImageMetadata(stream.width, stream.height, stream.duration, stream.r_frame_rate.split("/")(0).toInt)
      }
  }
  object FfprobeOut {
    implicit val decoder: JsonDecoder[FfprobeOut] = DeriveJsonDecoder.gen[FfprobeOut]
  }

  private def parseMetadata(ffprobeJson: String) =
    ZIO.logInfo(s"Metadata: $ffprobeJson").as(MovingImageMetadata(0, 0, 0, 0)) *>
      ZIO
        .fromOption(ffprobeJson.fromJson[FfprobeOut].toOption.flatMap(_.toMetadata))
        .orElseFail(new RuntimeException(s"Failed parsing metadata: $ffprobeJson"))

}

object MovingImageService {
  val layer = CommandExecutor.layer >>> ZLayer.derive[MovingImageService]
}

final case class CommandExecutor() {
  private case class ProcessOutput(out: String, err: String)

  private class InMemoryProcessLogger extends ProcessLogger {
    private val sbOut = new StringBuilder
    private val sbErr = new StringBuilder

    override def out(s: => String): Unit = sbOut.append(s + "\n")

    override def err(s: => String): Unit = sbErr.append(s + "\n")

    override def buffer[T](f: => T): T = f

    def getOutput: ProcessOutput = ProcessOutput(sbOut.toString(), sbErr.toString())
  }

  def execute(command: String): Task[String] = {
    val logger = new InMemoryProcessLogger()
    for {
      _   <- ZIO.logInfo(s"Executing command: $command")
      out <- ZIO.attemptBlockingIO(command ! logger).as(logger.getOutput)
      _ <- ZIO.when(out.err.nonEmpty)(
             ZIO.fail(new RuntimeException(s"Failed executing '$command' with error '${out.err}''"))
           )
    } yield out.out
  }
}

object CommandExecutor {
  val layer = ZLayer.derive[CommandExecutor]
}

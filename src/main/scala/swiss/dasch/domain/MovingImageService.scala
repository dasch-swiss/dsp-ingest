package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain.DerivativeFile.MovingImageDerivativeFile
import swiss.dasch.infrastructure.CommandExecutor
import zio.json.{DecoderOps, DeriveJsonDecoder, JsonDecoder}
import zio.{Task, ZIO, ZLayer}

final case class MovingImageMetadata(width: Int, height: Int, duration: Double, fps: Int)

case class MovingImageService(storage: StorageService, executor: CommandExecutor) {

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

  def extractMetadata(file: DerivativeFile): Task[MovingImageMetadata] =
    for {
      absPath <- file.toPath.toAbsolutePath
      cmd <-
        executor.buildCommand(
          "ffprobe",
          s"-v error -select_streams v:0 -show_entries stream=width,height,duration,r_frame_rate -print_format json -i $absPath"
        )
      metadata <- executor
                    .execute(cmd)
                    .flatMap(out =>
                      if (out.stdErr.nonEmpty) {
                        ZIO.fail(new RuntimeException(s"Failed to extract metadata: ${out.stdErr}"))
                      } else ZIO.succeed(out.stdOut)
                    )
                    .flatMap(parseMetadata)

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

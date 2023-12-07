package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain.DerivativeFile.MovingImageDerivativeFile
import zio.{Task, ZIO, ZLayer}

case class MovingImageService(storage: StorageService) {

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
}

object MovingImageService {
  val layer = ZLayer.derive[MovingImageService]
}

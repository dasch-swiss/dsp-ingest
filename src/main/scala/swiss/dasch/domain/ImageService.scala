/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain.SipiImageFormat.Jpx
import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZStream

import java.io.IOException

trait ImageService {

  /** Apply top left correction to image if needed. Creates a backup of the original image "${image.filename}.bak" in
    * the same directory.
    * @param image
    *   the image to apply the correction to
    * @return
    *   the path to the corrected image or None if no correction was needed
    */
  def applyTopLeftCorrection(image: Path): Task[Option[Path]]
}
object ImageService {

  def isJpeg2000(p: Path): IO[IOException, Boolean] =
    isNonHiddenRegularFile(p) &&
    ZIO.succeed(Jpx.allExtensions.contains(FilenameUtils.getExtension(p.filename.toString)))

  def isImage(path: Path): IO[IOException, Boolean] =
    isNonHiddenRegularFile(path) &&
    ZIO.succeed(SipiImageFormat.allExtension.contains(FilenameUtils.getExtension(path.filename.toString)))

  private def isNonHiddenRegularFile(path: Path) = Files.isRegularFile(path) && Files.isHidden(path).negate

  def findJpeg2000Files(path: Path): ZStream[Any, Throwable, Path] = StorageService.findInPath(path, isJpeg2000)

  def applyTopLeftCorrection(image: Path): ZIO[ImageService, Throwable, Option[Path]] =
    ZIO.serviceWithZIO[ImageService](_.applyTopLeftCorrection(image))
}

// see also https://exiftool.org/TagNames/EXIF.html
object Exif {
  object Image {
    val Orientation = "Exif.Image.Orientation"

    sealed trait OrientationValue { def value: Char }
    object OrientationValue       {
      // = Horizontal(normal)
      case object Horizontal extends OrientationValue { val value = '1' }

      // = Rotate 270 CW
      case object Rotate270CW extends OrientationValue { val value = '8' }
    }
  }
}

final case class ImageServiceLive(sipiClient: SipiClient, assetInfos: AssetInfoService) extends ImageService {

  override def applyTopLeftCorrection(image: Path): Task[Option[Path]] =
    ZIO.whenZIO(needsTopLeftCorrection(image))(
      ZIO.logInfo(s"Applying top left correction to $image") *>
        Files.copy(image, image.parent.map(_ / s"${image.filename}.bak").orNull) *>
        sipiClient.applyTopLeftCorrection(image, image) *>
        assetInfos.updateAssetInfoForDerivative(image).as(image)
    )

  private def needsTopLeftCorrection(image: Path): IO[IOException, Boolean] =
    ImageService.isImage(image) &&
    sipiClient
      .queryImageFile(image)
      .map(_.stdOut.split('\n'))
      .map { lines =>
        // check if the image has an orientation tag and if it is not horizontal
        lines
          .filter(_.startsWith(Exif.Image.Orientation))
          .exists(_.lastOption.exists(_ != Exif.Image.OrientationValue.Horizontal.value))
      }
}

object ImageServiceLive {
  val layer = ZLayer.fromFunction(ImageServiceLive.apply _)
}

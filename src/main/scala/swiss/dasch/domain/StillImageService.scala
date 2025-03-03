/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import swiss.dasch.domain.AugmentedPath.{JpxDerivativeFile, OrigFile}
import swiss.dasch.domain.SipiImageFormat.Jpx
import zio.*
import zio.nio.file.{Files, Path}

import java.io.IOException

final case class StillImageService(
  sipiClient: SipiClient,
  assetInfos: AssetInfoService,
  mimeTypeGuesser: MimeTypeGuesser,
) {

  /**
   * Apply top left correction to the image if needed.
   *
   * Creates a backup of the original image "${image.filename}.bak" in the same directory.
   *
   * Updates the asset info for the derivative.
   *
   * @param image
   * the image to apply the correction to
   * @return
   * the path to the corrected image or None if no correction was needed
   */
  def applyTopLeftCorrection(image: Path): Task[Option[Path]] =
    ZIO.whenZIO(needsTopLeftCorrection(image))(
      ZIO.logInfo(s"Applying top left correction to $image") *>
        Files.copy(image, image.parent.map(_ / s"${image.filename}.bak").orNull) *>
        sipiClient.applyTopLeftCorrection(image, image) *>
        assetInfos.updateAssetInfoForDerivative(image).as(image),
    )

  def needsTopLeftCorrection(image: Path): IO[IOException, Boolean] =
    FileFilters.isStillImage(image) &&
      sipiClient
        .queryImageFile(image)
        .map(_.stdout.split('\n'))
        .map { lines =>
          // check if the image has an orientation tag and if it is not horizontal
          lines
            .filter(_.startsWith(Exif.Image.Orientation))
            .exists(_.lastOption.exists(_ != Exif.Image.OrientationValue.Horizontal.value))
        }

  def createDerivative(orig: OrigFile): Task[JpxDerivativeFile] = {
    val jpxFile = JpxDerivativeFile.unsafeFrom(orig.path.parent.head / s"${orig.assetId}.${Jpx.extension}")
    sipiClient.transcodeImageFile(orig.path, jpxFile.path, Jpx) *>
      ZIO
        .fail(new IOException(s"Sipi failed creating derivative for ${orig.path}"))
        .whenZIO(Files.notExists(jpxFile.path))
        .as(jpxFile)
  }

  def extractMetadata(original: Original, derivative: JpxDerivativeFile): Task[StillImageMetadata] = for {
    _                 <- ZIO.when(original.assetId != derivative.assetId)(ZIO.die(new Exception("Asset IDs do not match")))
    _                 <- ZIO.logInfo(s"Extracting metadata for ${derivative.assetId}")
    dim               <- getDimensions(derivative)
    originalMimeType   = mimeTypeGuesser.guess(original.originalFilename)
    derivativeMimeType = mimeTypeGuesser.guess(derivative.file)
  } yield StillImageMetadata(dim, derivativeMimeType, originalMimeType)

  def getDimensions(derivative: JpxDerivativeFile): Task[Dimensions] =
    sipiClient
      .queryImageFile(derivative.path)
      .map(out => out.stdout.split('\n'))
      .flatMap { lines =>
        def getPositiveInt(key: String): Option[Int Refined Positive] =
          lines
            .find(_.startsWith(key))
            .flatMap(_.split('=').lastOption.map(_.trim))
            .flatMap(_.toIntOption)
            .flatMap(i => refineV[Positive](i).toOption)
        val dim = for {
          width  <- getPositiveInt("nx")
          height <- getPositiveInt("ny")
        } yield Dimensions(width, height)
        dim match {
          case Some(d) => ZIO.succeed(d)
          case None    => ZIO.fail(new IOException(s"Could not get dimensions from '${derivative.path}'"))
        }
      }
}

object StillImageService {

  def applyTopLeftCorrection(image: Path): ZIO[StillImageService, Throwable, Option[Path]] =
    ZIO.serviceWithZIO[StillImageService](_.applyTopLeftCorrection(image))

  def needsTopLeftCorrection(image: Path): ZIO[StillImageService, IOException, Boolean] =
    ZIO.serviceWithZIO[StillImageService](_.needsTopLeftCorrection(image))

  def createDerivative(original: OrigFile): ZIO[StillImageService, Throwable, JpxDerivativeFile] =
    ZIO.serviceWithZIO[StillImageService](_.createDerivative(original))

  def getDimensions(file: JpxDerivativeFile): ZIO[StillImageService, Throwable, Dimensions] =
    ZIO.serviceWithZIO[StillImageService](_.getDimensions(file))

  val layer = ZLayer.derive[StillImageService]
}

/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import swiss.dasch.domain.SipiImageFormat.Jpx
import zio.*
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.nio.file.{Files, Path}
import zio.json.interop.refined.*

import DerivativeFile.JpxDerivativeFile

import java.io.IOException

final case class Dimensions(width: Int Refined Positive, height: Int Refined Positive)
object Dimensions {
  given codec: JsonCodec[Dimensions] = DeriveJsonCodec.gen[Dimensions]
}

trait StillImageService {

  /**
   * Apply top left correction to the image if needed.
   *
   * Creates a backup of the original image "${image.filename}.bak" in the same directory.
   *
   * Updates the asset info for the derivative.
   *
   * @param image
   *   the image to apply the correction to
   * @return
   *   the path to the corrected image or None if no correction was needed
   */
  def applyTopLeftCorrection(image: Path): Task[Option[Path]]

  def needsTopLeftCorrection(image: Path): IO[IOException, Boolean]

  def createDerivative(original: OriginalFile): Task[JpxDerivativeFile]

  def getDimensions(file: JpxDerivativeFile): Task[Dimensions]
}

object StillImageService {
  def applyTopLeftCorrection(image: Path): ZIO[StillImageService, Throwable, Option[Path]] =
    ZIO.serviceWithZIO[StillImageService](_.applyTopLeftCorrection(image))
  def needsTopLeftCorrection(image: Path): ZIO[StillImageService, IOException, Boolean] =
    ZIO.serviceWithZIO[StillImageService](_.needsTopLeftCorrection(image))
  def createDerivative(original: OriginalFile): ZIO[StillImageService, Throwable, JpxDerivativeFile] =
    ZIO.serviceWithZIO[StillImageService](_.createDerivative(original))
  def getDimensions(file: JpxDerivativeFile): ZIO[StillImageService, Throwable, Dimensions] =
    ZIO.serviceWithZIO[StillImageService](_.getDimensions(file))
}

final case class StillImageServiceLive(sipiClient: SipiClient, assetInfos: AssetInfoService) extends StillImageService {

  override def applyTopLeftCorrection(image: Path): Task[Option[Path]] =
    ZIO.whenZIO(needsTopLeftCorrection(image))(
      ZIO.logInfo(s"Applying top left correction to $image") *>
        Files.copy(image, image.parent.map(_ / s"${image.filename}.bak").orNull) *>
        sipiClient.applyTopLeftCorrection(image, image) *>
        assetInfos.updateAssetInfoForDerivative(image).as(image)
    )

  override def needsTopLeftCorrection(image: Path): IO[IOException, Boolean] =
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

  override def createDerivative(original: OriginalFile): Task[JpxDerivativeFile] = {
    val imagePath      = original.toPath
    val derivativePath = imagePath.parent.head / s"${original.assetId}.${Jpx.extension}"
    ZIO.logInfo(s"Creating derivative for $imagePath") *>
      sipiClient.transcodeImageFile(imagePath, derivativePath, Jpx) *>
      ZIO
        .fail(new IOException(s"Sipi failed creating derivative for $imagePath"))
        .whenZIO(Files.notExists(derivativePath))
        .as(JpxDerivativeFile.unsafeFrom(derivativePath))
  }

  override def getDimensions(derivative: JpxDerivativeFile): Task[Dimensions] = {
    val path = derivative.toPath
    sipiClient
      .queryImageFile(path)
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
          case None    => ZIO.fail(new IOException(s"Could not get dimensions from '$path'"))
        }
      }
  }
}

object StillImageServiceLive {
  val layer = ZLayer.derive[StillImageServiceLive]
}
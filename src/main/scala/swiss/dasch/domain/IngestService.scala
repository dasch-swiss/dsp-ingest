/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.Asset.{OtherAsset, StillImageAsset}
import swiss.dasch.domain.DerivativeFile.OtherDerivativeFile
import swiss.dasch.domain.PathOps.fileExtension
import zio.nio.file.Path
import zio.{IO, Task, ZIO, ZLayer}

import java.io.{FileNotFoundException, IOException}

final case class IngestedFile()
final case class IngestService(
  storage: StorageService,
  imageService: ImageService,
  sipiClient: SipiClient,
  assetInfo: AssetInfoService
) {
  def ingestFile(
    fileToIngest: Path,
    project: ProjectShortcode
  ): Task[Asset] =
    for {
      _        <- ZIO.logInfo(s"Ingesting file $fileToIngest")
      assetRef <- AssetRef.makeNew(project)
      _ <- ZIO.whenZIO(FileFilters.isNonHiddenRegularFile(fileToIngest).negate)(
             ZIO.fail(new FileNotFoundException(s"File $fileToIngest is not a regular file"))
           )
      _        <- storage.getAssetDirectory(assetRef).tap(storage.createDirectories(_))
      original <- createOriginalFileInAssetDir(fileToIngest, assetRef)
      asset <- ZIO
                 .fromOption(SupportedFileType.fromPath(fileToIngest))
                 .orElseFail(new IllegalArgumentException("Unsupported file type."))
                 .flatMap { it =>
                   it match {
                     case SupportedFileType.StillImage => handleImageFile(original, assetRef)
                     case SupportedFileType.Other      => handleOtherFile(original, assetRef)
                     case SupportedFileType.MovingImage =>
                       ZIO.fail(new NotImplementedError("Video files are not supported yet."))
                   }
                 }
      _ <- assetInfo.createAssetInfo(asset)
      _ <- storage.delete(fileToIngest)
      _ <- ZIO.logInfo(s"Finished ingesting file $fileToIngest")
    } yield asset

  private def createOriginalFileInAssetDir(file: Path, assetRef: AssetRef): IO[IOException, Original] = for {
    _               <- ZIO.logInfo(s"Creating original for $file, $assetRef")
    assetDir        <- storage.getAssetDirectory(assetRef)
    originalPath     = assetDir / s"${assetRef.id}.${file.fileExtension}.orig"
    _               <- storage.copyFile(file, originalPath)
    originalFile     = OriginalFile.unsafeFrom(originalPath)
    originalFileName = NonEmptyString.unsafeFrom(file.filename.toString)
  } yield Original(originalFile, originalFileName)

  private def handleImageFile(original: Original, assetRef: AssetRef): Task[StillImageAsset] =
    ZIO.logInfo(s"Creating derivative for image $original, $assetRef") *>
      imageService
        .createDerivative(original.file)
        .map(derivative => Asset.makeStillImage(assetRef, original, derivative))

  private def handleOtherFile(original: Original, assetRef: AssetRef): Task[OtherAsset] = {
    def createDerivative(original: Original) =
      for {
        folder        <- storage.getAssetDirectory(assetRef)
        derivativePath = folder / s"${assetRef.id}.${original.file.toPath.fileExtension}"
        _             <- storage.copyFile(original.file.toPath, derivativePath)
      } yield OtherDerivativeFile.unsafeFrom(derivativePath)

    ZIO.logInfo(s"Creating derivative for other $original, $assetRef") *>
      createDerivative(original).map(derivative => Asset.makeOther(assetRef, original, derivative))
  }
}

object IngestService {
  def layer = ZLayer.derive[IngestService]
}

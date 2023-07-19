/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import zio.json.EncoderOps
import swiss.dasch.domain
import zio.*
import zio.nio.file.{ Files, Path }
import zio.stream.{ ZSink, ZStream }

object MaintenanceActions {

  def createOriginals(projectPath: Path, mapping: Map[String, String]) = findJpeg2000Files(projectPath)
    .flatMap(findAssetsWithoutOriginal(_, mapping))
    .mapZIOPar(8)(createOriginal(_).flatMap(c => updateAssetInfo(c)))
    .as(1)
    .run(ZSink.sum)

  private def findJpeg2000Files(projectPath: Path): ZStream[Any, Throwable, Path] =
    Files
      .walk(projectPath)
      .filterZIO(p => Files.isRegularFile(p) && Files.isHidden(p).negate && isJpeg2000File(p))

  private def isJpeg2000File(p: Path) = {
    val filename = p.filename.toString
    ZIO.succeed(filename.endsWith(".jpx") || filename.endsWith(".jp2"))
  }

  final private case class CreateOriginalFor(
      assetId: AssetId,
      jpxPath: Path,
      targetFormat: SipiImageFormat,
      originalFilename: String,
    ) {
    def originalPath: Path = jpxPath.parent.map(_ / s"$assetId.${targetFormat.extension}.orig").orNull
  }

  private def findAssetsWithoutOriginal(jpxPath: Path, mapping: Map[String, String])
      : ZStream[Any, Throwable, CreateOriginalFor] =
    AssetId.makeFromPath(jpxPath) match {
      case Some(assetId) => filterWithoutOriginal(assetId, jpxPath, mapping)
      case None          => ZStream.logWarning(s"Not an assetId: $jpxPath") *> ZStream.empty
    }

  private def filterWithoutOriginal(
      assetId: AssetId,
      jpxPath: Path,
      mapping: Map[String, String],
    ): ZStream[Any, Throwable, CreateOriginalFor] = {
    val fallBackFormat             = Tif
    val originalFilenameMaybe      = mapping.get(jpxPath.filename.toString)
    val originalFileExtensionMaybe = originalFilenameMaybe.map(FilenameUtils.getExtension).filter(_ != null)
    val targetFormat               = originalFileExtensionMaybe.flatMap(SipiImageFormat.fromExtension).getOrElse(fallBackFormat)
    val originalFilename           = originalFilenameMaybe
      .map(fileName =>
        if (fileName.endsWith(s".${targetFormat.extension}")) fileName
        else
          fileName.replace(s".${originalFileExtensionMaybe.getOrElse(targetFormat.extension)}", targetFormat.extension)
      )
      .getOrElse(s"$assetId.${targetFormat.extension}")

    val createThis =
      CreateOriginalFor(assetId, jpxPath, targetFormat, originalFilename)
    ZStream
      .fromZIO(Files.exists(createThis.originalPath))
      .flatMap {
        case true  =>
          ZStream.logInfo(s"Original for $jpxPath present, skipping ${createThis.originalPath}") *> ZStream.empty
        case false =>
          ZStream.logDebug(s"Original for $jpxPath not present") *> ZStream.succeed(createThis)
      }
  }

  private def createOriginal(c: CreateOriginalFor) =
    ZIO.logInfo(s"Creating ${c.originalPath} for ${c.jpxPath}") *>
      SipiClient
        .transcodeImageFile(fileIn = c.jpxPath, fileOut = c.originalPath, outputFormat = c.targetFormat)
        .map(sipiOut => (c.assetId, c.jpxPath, c.originalPath, sipiOut))
        .tap(it => ZIO.logDebug(it.toString()))
        .as(c)

  private def updateAssetInfo(c: CreateOriginalFor) = {

    val infoFilePath = c.jpxPath.parent.orNull / s"${c.assetId}.info"
    for {
      _    <- ZIO.logInfo(s"Updating ${c.assetId} info file $infoFilePath")
      info <- createNewAssetInfoFileContent(c)
      _    <- Files.deleteIfExists(infoFilePath) *> Files.createFile(infoFilePath)
      _    <- Files.writeBytes(infoFilePath, Chunk.fromArray(info.toJsonPretty.getBytes))
    } yield ()
  }

  private def createNewAssetInfoFileContent(c: CreateOriginalFor)
      : ZIO[FileChecksumService, Throwable, AssetInfoFileContent] =
    for {
      checksumOriginal   <- FileChecksumService.createSha256Hash(c.originalPath)
      checksumDerivative <- FileChecksumService.createSha256Hash(c.jpxPath)
    } yield AssetInfoFileContent(
      internalFilename = c.jpxPath.filename.toString,
      originalInternalFilename = c.originalPath.filename.toString,
      originalFilename = c.originalFilename,
      checksumOriginal = checksumOriginal.toString,
      checksumDerivative = checksumDerivative.toString,
    )

}

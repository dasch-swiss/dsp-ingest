/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.json.EncoderOps
import swiss.dasch.domain
import zio.*
import zio.nio.file.{ Files, Path }
import zio.stream.{ ZSink, ZStream }

import java.io.IOException

object MaintenanceActions {

  private val targetFormat               = Tif
  def createOriginals(projectPath: Path) = for {
    mapping <- loadCsv()
    count   <- findJpeg2000Files(projectPath)
                 .flatMap(findAssetsWithoutOriginal)
                 .mapZIOPar(8)(createOriginal(_).flatMap(c => updateAssetInfo(c)))
                 .as(1)
                 .run(ZSink.sum)
  } yield count

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
      originalPath: Path,
    )

  private def findAssetsWithoutOriginal(jpxPath: Path): ZStream[Any, Throwable, CreateOriginalFor] =
    AssetId.makeFromPath(jpxPath) match {
      case Some(assetId) => filterWithoutOriginal(assetId, jpxPath)
      case None          => ZStream.logWarning(s"Not an assetId: $jpxPath") *> ZStream.empty
    }

  private def filterWithoutOriginal(assetId: AssetId, jpxPath: Path): ZStream[Any, Throwable, CreateOriginalFor] = {
    val originalPath: Path = jpxPath.parent.map(_ / s"$assetId.${targetFormat.extension}.orig").orNull
    ZStream
      .fromZIO(Files.exists(originalPath))
      .flatMap {
        case true  =>
          ZStream.logInfo(s"Original for $jpxPath present, skipping $originalPath") *>
            ZStream.empty
        case false =>
          ZStream.logDebug(s"Original for $jpxPath not present") *>
            ZStream.succeed(CreateOriginalFor(assetId, jpxPath, originalPath))
      }
  }

  private def createOriginal(c: CreateOriginalFor) =
    ZIO.logInfo(s"Creating ${c.originalPath} for ${c.jpxPath}") *>
      SipiClient
        .transcodeImageFile(fileIn = c.jpxPath, fileOut = c.originalPath, outputFormat = targetFormat)
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
      originalFilename   <- lookupOriginalFilename(c)
    } yield AssetInfoFileContent(
      internalFilename = c.jpxPath.filename.toString,
      originalInternalFilename = c.originalPath.filename.toString,
      originalFilename = originalFilename,
      checksumOriginal = checksumOriginal.toString,
      checksumDerivative = checksumDerivative.toString,
    )

  private def lookupOriginalFilename(c: CreateOriginalFor): Task[String] =
    for {
      mapping <- loadCsv()
    } yield mapping.getOrElse(
      c.jpxPath.filename.toString,
      s"${c.assetId}.${targetFormat.extension}",
    )

  private def loadCsv(): ZIO[Any, IOException, Map[String, String]] =
    Files
      .readAllLines(
        Path(
          "/Users/christian/git/dasch/dsp-ingest/src/main/resources/maintenance/create-originals/0801-dev-original-internal-filename-mapping.csv"
        )
      )
      .map(
        _.map(_.split(","))
          .map(it => (it(0).replace("\"", ""), it(1).replace("\"", "")))
          .toMap
      )
}

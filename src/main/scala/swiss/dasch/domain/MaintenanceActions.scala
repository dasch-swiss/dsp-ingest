/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.StorageService.maxParallelism
import zio.*
import zio.nio.file.{ Files, Path }
import zio.stream.ZStream

import java.io.IOException

object MaintenanceActions {

  private val targetFormat = Tif
  def createTifOriginals(projectPath: Path)
      : ZStream[SipiClient with ProjectService, Throwable, (AssetId, Path, Path, SipiOutput)] =
    findJpxFiles(projectPath)
      .flatMap(findAssetsWithoutOriginal)
      .mapZIOPar(20) {
        case (assetId, jpxPath) =>
          val originalPath = getOriginalPath(assetId, jpxPath)
          ZIO.logInfo(s"Creating $originalPath for $jpxPath") *>
            SipiClient
              .transcodeImageFile(fileIn = jpxPath, fileOut = originalPath, outputFormat = targetFormat)
              .map(sipiOut => (assetId, jpxPath, originalPath, sipiOut))
      }

  private def findJpxFiles(projectPath: Path): ZStream[Any, Throwable, Path] =
    Files
      .walk(projectPath)
      .filterZIO(p =>
        Files.isRegularFile(p) && Files.isHidden(p).negate && ZIO.succeed(p.filename.toString.endsWith(".jpx"))
      )

  private def findAssetsWithoutOriginal(jpxPath: Path): ZStream[Any, Throwable, (AssetId, Path)] =
    AssetId.makeFromPath(jpxPath) match {
      case Some(assetId) => filterWithoutOriginal(assetId, jpxPath)
      case None          => ZStream.logInfo(s"Not an assetId: $jpxPath") *> ZStream.empty
    }

  private def filterWithoutOriginal(assetId: AssetId, jpxPath: Path): ZStream[Any, Throwable, (AssetId, Path)] = {
    val originalPath: Path = getOriginalPath(assetId, jpxPath)
    ZStream
      .fromZIO(Files.exists(originalPath))
      .flatMap {
        case true  => ZStream.logInfo(s"Original for $jpxPath present: $originalPath") *> ZStream.empty
        case false => ZStream.logInfo(s"Original for $jpxPath not present") *> ZStream.succeed((assetId, jpxPath))
      }
  }

  private def getOriginalPath(assetId: AssetId, jpxPath: Path) =
    jpxPath.parent.map(_ / s"$assetId.${targetFormat.extension}.orig").orNull
}

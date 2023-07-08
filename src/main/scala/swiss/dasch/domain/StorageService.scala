/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.json.{ DecoderOps, DeriveJsonCodec, JsonCodec }
import zio.nio.file.{ Files, Path }

import java.io.IOException

final private case class AssetInfoFileContent(
    internalFilename: String,
    originalInternalFilename: String,
    originalFilename: String,
    checksumOriginal: String,
    checksumDerivative: String,
  )

object AssetInfoFileContent {
  implicit val codec: JsonCodec[AssetInfoFileContent] = DeriveJsonCodec.gen[AssetInfoFileContent]
}
final case class AssetInfo(
    derivativeFile: Path,
    originalFile: Path,
    originalFilename: String,
    checksumOriginal: String,
    checksumDerivative: String,
  )

trait StorageService  {
  def getProjectDirectory(projectShortcode: ProjectShortcode): UIO[Path]
  def getAssetDirectory(asset: Asset): UIO[Path]
  def loadInfoFile(asset: Asset): Task[AssetInfo]
}
object StorageService {
  def getProjectDirectory(projectShortcode: ProjectShortcode): RIO[StorageService, Path] =
    ZIO.serviceWithZIO[StorageService](_.getProjectDirectory(projectShortcode))
  def getAssetDirectory(asset: Asset): RIO[StorageService, Path]                         =
    ZIO.serviceWithZIO[StorageService](_.getAssetDirectory(asset))
  def loadInfoFile(asset: Asset): ZIO[StorageService, Throwable, AssetInfo]              =
    ZIO.serviceWithZIO[StorageService](_.loadInfoFile(asset))
}

final case class StorageServiceLive(config: StorageConfig) extends StorageService {
  override def getProjectDirectory(projectShortcode: ProjectShortcode): UIO[Path] =
    ZIO.succeed(config.assetPath / projectShortcode.toString)
  override def getAssetDirectory(asset: Asset): UIO[Path]                         =
    getProjectDirectory(asset.belongsToProject).map(_ / segments(asset.id))
  private def segments(assetId: AssetId): Path                                    = {
    val assetString = assetId.toString
    val segment1    = assetString.substring(0, 2)
    val segment2    = assetString.substring(2, 4)
    Path(segment1.toLowerCase, segment2.toLowerCase)
  }

  override def loadInfoFile(asset: Asset): Task[AssetInfo] =
    for {
      infoFile <- getInfoFilePath(asset)
      assetDir <- getAssetDirectory(asset)
      content  <- parseAssetInfoFile(asset, infoFile, assetDir)
    } yield content

  private def getInfoFilePath(asset: Asset): UIO[Path] =
    getAssetDirectory(asset).map(_ / s"${asset.id.toString}.info")

  private def parseAssetInfoFile(
      asset: Asset,
      infoFile: Path,
      assetDir: Path,
    ): Task[AssetInfo] = Files
    .readAllLines(infoFile)
    .logError(s"Unable to load info file for $asset")
    .map(_.mkString.fromJson[AssetInfoFileContent])
    .absolve
    .mapBoth(
      {
        case s: String    => new IllegalStateException(s"Unable to parse info file for $asset: $s")
        case e: Throwable => e
      },
      toAssetInfo(_, assetDir),
    )

  private def toAssetInfo(raw: AssetInfoFileContent, assetDirectory: Path): AssetInfo =
    AssetInfo(
      derivativeFile = assetDirectory / raw.internalFilename,
      originalFile = assetDirectory / raw.originalInternalFilename,
      originalFilename = raw.originalFilename,
      checksumOriginal = raw.checksumOriginal,
      checksumDerivative = raw.checksumDerivative,
    )
}
object StorageServiceLive {
  val layer = ZLayer.fromFunction(StorageServiceLive.apply _)
}

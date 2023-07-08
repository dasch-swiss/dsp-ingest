/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.prelude.Validation
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

final case class FileAndChecksum(file: Path, checksum: Sha256Hash)
final case class AssetInfo(
    original: FileAndChecksum,
    originalFilename: NonEmptyString,
    derivative: FileAndChecksum,
  )

trait StorageService  {
  def getProjectDirectory(projectShortcode: ProjectShortcode): UIO[Path]
  def getAssetDirectory(asset: Asset): UIO[Path]
  def getAssetDirectory(): UIO[Path]
  def getTempDirectory(): UIO[Path]
  def loadInfoFile(asset: Asset): Task[AssetInfo]
}
object StorageService {
  def getProjectDirectory(projectShortcode: ProjectShortcode): RIO[StorageService, Path] =
    ZIO.serviceWithZIO[StorageService](_.getProjectDirectory(projectShortcode))
  def getAssetDirectory(asset: Asset): RIO[StorageService, Path]                         =
    ZIO.serviceWithZIO[StorageService](_.getAssetDirectory(asset))
  def getAssetDirectory(): RIO[StorageService, Path]                                     =
    ZIO.serviceWithZIO[StorageService](_.getAssetDirectory())
  def getTempDirectory(): RIO[StorageService, Path]                                      =
    ZIO.serviceWithZIO[StorageService](_.getTempDirectory())
  def loadInfoFile(asset: Asset): ZIO[StorageService, Throwable, AssetInfo]              =
    ZIO.serviceWithZIO[StorageService](_.loadInfoFile(asset))
}

final case class StorageServiceLive(config: StorageConfig) extends StorageService {
  override def getTempDirectory(): UIO[Path]                                      =
    ZIO.succeed(config.tempPath)
  override def getAssetDirectory(): UIO[Path]                                     =
    ZIO.succeed(config.assetPath)
  override def getProjectDirectory(projectShortcode: ProjectShortcode): UIO[Path] =
    getAssetDirectory().map(_ / projectShortcode.toString)
  override def getAssetDirectory(asset: Asset): UIO[Path]                         =
    getProjectDirectory(asset.belongsToProject).map(_ / segments(asset.id))

  private def segments(assetId: AssetId): Path = {
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
    .flatMap(lines => parseJson(lines.mkString, asset))
    .flatMap(toAssetInfo(_, assetDir))

  private def parseJson(json: String, asset: Asset) =
    ZIO
      .fromEither(json.fromJson[AssetInfoFileContent])
      .mapError(errMsg => IllegalArgumentException(s"Unable to parse info file content for $asset: $errMsg"))

  private def toAssetInfo(raw: AssetInfoFileContent, assetDir: Path): Task[AssetInfo] =
    Validation
      .validateWith(
        Validation.fromEither(Sha256Hash.make(raw.checksumOriginal)),
        Validation.fromEither(Sha256Hash.make(raw.checksumDerivative)),
        Validation.fromEither(NonEmptyString.from(raw.originalFilename)),
      ) {
        (
            origChecksum,
            derivativeChecksum,
            origFilename,
          ) =>
          AssetInfo(
            original = FileAndChecksum(assetDir / raw.originalInternalFilename, origChecksum),
            originalFilename = origFilename,
            derivative = FileAndChecksum(assetDir / raw.internalFilename, derivativeChecksum),
          )
      }
      .toZIO
      .mapError(e => new IllegalArgumentException(s"Invalid asset info file content $raw, $assetDir, $e"))
}
object StorageServiceLive {
  val layer = ZLayer.fromFunction(StorageServiceLive.apply _)
}

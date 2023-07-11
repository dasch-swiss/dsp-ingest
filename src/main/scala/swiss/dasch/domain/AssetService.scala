/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.string.{ MatchesRegex, Trimmed }
import eu.timepit.refined.types.string.NonEmptyString
import zio.*
import zio.json.{ DecoderOps, DeriveJsonCodec, JsonCodec }
import zio.nio.file.{ Files, Path }

import java.io.IOException

opaque type AssetId = String Refined MatchesRegex["^[a-zA-Z0-9-]{4,}$"]

object AssetId {
  def make(id: String): Either[String, AssetId] = refineV(id)
}
final case class Asset(id: AssetId, belongsToProject: ProjectShortcode)
final case class ChecksumResult(file: Path, checksumMatches: Boolean)

trait AssetService  {
  def verifyChecksumOrig(asset: Asset): Task[Boolean]

  def verifyChecksumDerivative(asset: Asset): Task[Boolean]

  def verifyChecksum(assetInfo: AssetInfo): Task[Chunk[ChecksumResult]]
}
object AssetService {
  def verifyChecksumOrig(asset: Asset): ZIO[AssetService, Throwable, Boolean]                   =
    ZIO.serviceWithZIO[AssetService](_.verifyChecksumOrig(asset))
  def verifyChecksumDerivative(asset: Asset): ZIO[AssetService, Throwable, Boolean]             =
    ZIO.serviceWithZIO[AssetService](_.verifyChecksumDerivative(asset))
  def verifyChecksum(assetInfo: AssetInfo): ZIO[AssetService, Throwable, Chunk[ChecksumResult]] =
    ZIO.serviceWithZIO[AssetService](_.verifyChecksum(assetInfo))
}

final case class AssetServiceLive(storage: StorageService, checksum: FileChecksum) extends AssetService {
  override def verifyChecksumOrig(asset: Asset): Task[Boolean] =
    verifyChecksum(asset, _.original)

  override def verifyChecksumDerivative(asset: Asset): Task[Boolean] =
    verifyChecksum(asset, _.derivative)

  private def verifyChecksum(asset: Asset, checksumAndFile: AssetInfo => FileAndChecksum): Task[Boolean] =
    storage.loadInfoFile(asset).map(checksumAndFile).flatMap(verifyChecksum)

  private def verifyChecksum(fileAndChecksum: FileAndChecksum): Task[Boolean] =
    for {
      checksumCalculated <- checksum
                              .createSha256Hash(fileAndChecksum.file)
                              .logError(s"Unable to calculate checksum for ${fileAndChecksum.file}")
    } yield fileAndChecksum.checksum == checksumCalculated

  override def verifyChecksum(assetInfo: AssetInfo): Task[Chunk[ChecksumResult]] = {
    val original   = assetInfo.original
    val derivative = assetInfo.derivative
    for {
      origResult       <- verifyChecksum(original).map(ChecksumResult(original.file, _))
      derivativeResult <- verifyChecksum(derivative).map(ChecksumResult(derivative.file, _))
    } yield Chunk(origResult, derivativeResult)
  }
}

object AssetServiceLive {
  val layer = ZLayer.fromFunction(AssetServiceLive.apply _)
}

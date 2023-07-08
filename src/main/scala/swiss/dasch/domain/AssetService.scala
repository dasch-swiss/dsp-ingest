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

opaque type AssetId = String Refined MatchesRegex["^[a-zA-Z0-9-]+$"]

object AssetId      {
  def make(id: String): Either[String, AssetId] = refineV(id)
}
final case class Asset(id: AssetId, belongsToProject: ProjectShortcode)
trait AssetService  {
  def verifyChecksumOrig(asset: Asset): Task[Boolean]

  def verifyChecksumDerivative(asset: Asset): Task[Boolean]
}
object AssetService {
  def verifyChecksumOrig(asset: Asset): ZIO[AssetService, Throwable, Boolean] =
    ZIO.serviceWithZIO[AssetService](_.verifyChecksumOrig(asset))

  def verifyChecksumDerivative(asset: Asset): ZIO[AssetService, Throwable, Boolean] =
    ZIO.serviceWithZIO[AssetService](_.verifyChecksumDerivative(asset))
}

final case class AssetServiceLive(storage: StorageService, checksum: FileChecksum) extends AssetService {
  override def verifyChecksumOrig(asset: Asset): Task[Boolean] =
    verifyChecksum(asset, _.getOrigChecksumAndFile)

  override def verifyChecksumDerivative(asset: Asset): Task[Boolean] =
    verifyChecksum(asset, _.getDerivativeChecksumAndFile)

  private def verifyChecksum(
      asset: Asset,
      checksumAndFile: AssetInfo => (String, Path),
    ): Task[Boolean] =
    for {
      infoFile                <- storage.loadInfoFile(asset)
      (checksumExpected, file) = checksumAndFile(infoFile)
      checksumCalculated      <- checksum
                                   .createHashSha256(file)
                                   .logError(s"Unable to calculate checksum for $file of $asset")
    } yield checksumExpected == checksumCalculated
}

object AssetServiceLive {
  val layer = ZLayer.fromFunction(AssetServiceLive.apply _)
}

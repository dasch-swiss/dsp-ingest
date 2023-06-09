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

import java.io.{ FileInputStream, FileNotFoundException, IOException }

opaque type Sha256Hash = String Refined MatchesRegex["^[A-Fa-f0-9]{64}$"]
object Sha256Hash {
  def make(value: String): Either[String, Sha256Hash] = refineV(value)
}
final case class ChecksumResult(file: Path, checksumMatches: Boolean)

trait FileChecksumService  {
  def verifyChecksumOrig(asset: Asset): Task[Boolean]
  def verifyChecksumDerivative(asset: Asset): Task[Boolean]
  def verifyChecksum(assetInfo: AssetInfo): Task[Chunk[ChecksumResult]]
  def createSha256Hash(path: Path): IO[FileNotFoundException, Sha256Hash]
}
object FileChecksumService {
  def verifyChecksumOrig(asset: Asset): ZIO[FileChecksumService, Throwable, Boolean]                   =
    ZIO.serviceWithZIO[FileChecksumService](_.verifyChecksumOrig(asset))
  def verifyChecksumDerivative(asset: Asset): ZIO[FileChecksumService, Throwable, Boolean]             =
    ZIO.serviceWithZIO[FileChecksumService](_.verifyChecksumDerivative(asset))
  def verifyChecksum(assetInfo: AssetInfo): ZIO[FileChecksumService, Throwable, Chunk[ChecksumResult]] =
    ZIO.serviceWithZIO[FileChecksumService](_.verifyChecksum(assetInfo))
  def createSha256Hash(path: Path): ZIO[FileChecksumService, FileNotFoundException, Sha256Hash]        =
    ZIO.serviceWithZIO[FileChecksumService](_.createSha256Hash(path))
}

final case class FileChecksumServiceLive(assetInfos: AssetInfoService) extends FileChecksumService {
  override def verifyChecksumOrig(asset: Asset): Task[Boolean] =
    verifyChecksum(asset, _.original)

  override def verifyChecksumDerivative(asset: Asset): Task[Boolean] =
    verifyChecksum(asset, _.derivative)

  private def verifyChecksum(asset: Asset, checksumAndFile: AssetInfo => FileAndChecksum): Task[Boolean] =
    assetInfos.findByAsset(asset).map(checksumAndFile).flatMap(verifyChecksum)

  private def verifyChecksum(fileAndChecksum: FileAndChecksum): Task[Boolean] =
    createSha256Hash(fileAndChecksum.file)
      .logError(s"Unable to calculate checksum for ${fileAndChecksum.file}")
      .map(_ == fileAndChecksum.checksum)

  override def verifyChecksum(assetInfo: AssetInfo): Task[Chunk[ChecksumResult]] = {
    val original   = assetInfo.original
    val derivative = assetInfo.derivative
    for {
      origResult       <- verifyChecksum(original).map(ChecksumResult(original.file, _))
      derivativeResult <- verifyChecksum(derivative).map(ChecksumResult(derivative.file, _))
    } yield Chunk(origResult, derivativeResult)
  }

  def createSha256Hash(path: Path): IO[FileNotFoundException, Sha256Hash] =
    ZIO.scoped(ScopedIoStreams.fileInputStream(path).flatMap(hashSha256))

  private def hashSha256(fis: FileInputStream): UIO[Sha256Hash] = {
    val digest    = java.security.MessageDigest.getInstance("SHA-256")
    val buffer    = new Array[Byte](8192)
    var bytesRead = 0
    while ({
      bytesRead = fis.read(buffer); bytesRead != -1
    }) digest.update(buffer, 0, bytesRead)
    val sb        = new StringBuilder
    for (byte <- digest.digest()) sb.append(String.format("%02x", Byte.box(byte)))
    ZIO.fromEither(Sha256Hash.make(sb.toString())).mapError(IllegalStateException(_)).orDie
  }
}

object FileChecksumServiceLive {
  val layer: URLayer[AssetInfoService, FileChecksumService] =
    ZLayer.fromFunction(FileChecksumServiceLive.apply _)
}

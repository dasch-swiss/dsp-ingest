/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import zio.*
import zio.nio.file.*

import java.io.{ FileInputStream, IOException }

opaque type Sha256Hash = String Refined MatchesRegex["^[A-Fa-f0-9]{64}$"]
object Sha256Hash  {
  def make(value: String): Either[String, Sha256Hash] = refineV(value)
}
trait FileChecksum {
  def createHashSha256(path: Path): Task[Sha256Hash]
}

object FileChecksum {
  def createHashSha256(path: Path): ZIO[FileChecksum, Throwable, Sha256Hash] =
    ZIO.serviceWithZIO[FileChecksum](_.createHashSha256(path))
}

case class FileChecksumLive() extends FileChecksum {

  def createHashSha256(path: Path): Task[Sha256Hash] =
    ZIO.scoped {
      for {
        fis        <- ZIO.acquireRelease(ZIO.attempt(new FileInputStream(path.toFile)))(fis => ZIO.succeed(fis.close()))
        hashString <- hashSha256(fis)
      } yield hashString
    }

  private def hashSha256(fis: FileInputStream): UIO[Sha256Hash] = {
    val digest    = java.security.MessageDigest.getInstance("SHA-256")
    val buffer    = new Array[Byte](8192)
    var bytesRead = 0
    while ({ bytesRead = fis.read(buffer); bytesRead != -1 }) digest.update(buffer, 0, bytesRead)
    val sb        = new StringBuilder
    for (byte <- digest.digest()) sb.append(String.format("%02x", Byte.box(byte)))
    ZIO.fromEither(Sha256Hash.make(sb.toString())).mapError(IllegalStateException(_)).orDie
  }
}
object FileChecksumLive {
  val layer: ULayer[FileChecksum] = ZLayer.succeed(FileChecksumLive())
}

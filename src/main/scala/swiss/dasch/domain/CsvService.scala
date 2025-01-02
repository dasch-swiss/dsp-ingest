/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import com.github.tototoshi.csv.CSVWriter
import swiss.dasch.domain.SizeInBytesPerType.{SizeInBytesAudio, SizeInBytesMovingImages, SizeInBytesOther}
import swiss.dasch.domain.SupportedFileType.{Audio, MovingImage, OtherFiles, StillImage}
import zio.nio.file.Path
import zio.{Task, ZIO}

final case class AssetOverviewReportCsvRow(
  shortcode: ProjectShortcode,
  totalNrOfAssets: Int,
  nrOfStillImageAssets: Int,
  nrOfMovingImageAssets: Int,
  nrOfAudioAssets: Int,
  nrOfOtherAssets: Int,
  sizeOfStillImageOriginals: FileSize,
  sizeOfStillImageDerivatives: FileSize,
  sizeOfMovingImageOriginals: FileSize,
  sizeOfMovingImageDerivatives: FileSize,
  sizeOfMovingImageKeyframes: FileSize,
  sizeOfAudioOriginals: FileSize,
  sizeOfAudioDerivatives: FileSize,
  sizeOfOtherOriginals: FileSize,
  sizeOfOtherDerivatives: FileSize,
) {

  def toList: List[String | Number] = List(
    shortcode.value,
    totalNrOfAssets,
    nrOfStillImageAssets,
    nrOfMovingImageAssets,
    nrOfOtherAssets,
    nrOfAudioAssets,
    sizeOfStillImageOriginals.sizeInBytes,
    sizeOfStillImageDerivatives.sizeInBytes,
    sizeOfMovingImageOriginals.sizeInBytes,
    sizeOfMovingImageDerivatives.sizeInBytes,
    sizeOfMovingImageKeyframes.sizeInBytes,
    sizeOfAudioOriginals.sizeInBytes,
    sizeOfAudioDerivatives.sizeInBytes,
    sizeOfOtherOriginals.sizeInBytes,
    sizeOfOtherDerivatives.sizeInBytes,
  )
}

object AssetOverviewReportCsvRow {
  val headerRow: List[String] = List(
    "shortcode",
    "totalNrOfAssets",
    "nrOfStillImageAssets",
    "nrOfMovingImageAssets",
    "nrOfAudioAssets",
    "nrOfOtherAssets",
    "sizeOfStillImageOriginals",
    "sizeOfStillImageDerivatives",
    "sizeOfMovingImageOriginals",
    "sizeOfMovingImageDerivatives",
    "sizeOfMovingImageKeyframes",
    "sizeOfAudioOriginals",
    "sizeOfAudioDerivatives",
    "sizeOfOtherOriginals",
    "sizeOfOtherDerivatives",
  )
  def fromReport(rep: AssetOverviewReport): AssetOverviewReportCsvRow = {
    def getFileSize(key: SupportedFileType, typ: String) =
      rep.sizesPerType.sizes.get(key) match {
        case None => FileSize(0)
        case Some(other: SizeInBytesOther) =>
          typ match {
            case "orig"       => other.sizeOrig
            case "derivative" => other.sizeDerivative
            case _            => throw new IllegalArgumentException(s"Unknown type: $typ")
          }
        case Some(still: SizeInBytesMovingImages) =>
          typ match {
            case "orig"       => still.sizeOrig
            case "derivative" => still.sizeDerivative
            case "keyframes"  => still.sizeKeyframes
            case _            => throw new IllegalArgumentException(s"Unknown type: $typ")
          }
        case Some(other: SizeInBytesAudio) =>
          typ match {
            case "orig"       => other.sizeOrig
            case "derivative" => other.sizeDerivative
            case _            => throw new IllegalArgumentException(s"Unknown type: $typ")
          }
      }

    AssetOverviewReportCsvRow(
      shortcode = rep.shortcode,
      totalNrOfAssets = rep.totalNrOfAssets,
      nrOfStillImageAssets = rep.nrOfAssetsPerType.getOrElse(StillImage, 0),
      nrOfMovingImageAssets = rep.nrOfAssetsPerType.getOrElse(MovingImage, 0),
      nrOfAudioAssets = rep.nrOfAssetsPerType.getOrElse(Audio, 0),
      nrOfOtherAssets = rep.nrOfAssetsPerType.getOrElse(OtherFiles, 0),
      getFileSize(StillImage, "orig"),
      getFileSize(StillImage, "derivative"),
      getFileSize(MovingImage, "orig"),
      getFileSize(MovingImage, "derivative"),
      getFileSize(MovingImage, "keyframes"),
      getFileSize(Audio, "orig"),
      getFileSize(Audio, "derivative"),
      getFileSize(OtherFiles, "orig"),
      getFileSize(OtherFiles, "derivative"),
    )
  }
}

final case class CsvService() {

  private def createWriter(path: Path) = {
    val acquire = ZIO.succeed(CSVWriter.open(path.toFile))
    val release = (w: CSVWriter) => ZIO.succeed(w.close())
    ZIO.acquireRelease(acquire)(release)
  }

  def writeReportToCsv(report: Seq[AssetOverviewReport], path: Path): Task[Path] =
    ZIO.scoped {
      for {
        writer <- createWriter(path)
        _      <- ZIO.succeed(writer.writeRow(AssetOverviewReportCsvRow.headerRow))
        _ <- ZIO.foreachDiscard(report)(rep =>
               ZIO.succeed(writer.writeRow(AssetOverviewReportCsvRow.fromReport(rep).toList)),
             )
      } yield path
    }
}

object CsvService {
  val layer = zio.ZLayer.derive[CsvService]
}

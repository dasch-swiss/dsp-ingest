package swiss.dasch.domain

import zio.*
import zio.nio.file.{ Files, Path }
import zio.stream.ZStream

import java.io.IOException

object MaintenanceActions {

  private val targetFormt = Tif
  def createOriginals(shortcode: String)
      : ZStream[SipiClient with ProjectService, Throwable, (AssetId, Path, Path, SipiOutput)] =
    ZStream
      .fromZIO(
        ZIO
          .succeed(ProjectShortcode.make(shortcode).toOption)
          .some
          .flatMap(ProjectService.findProject(_).some.tap(code => ZIO.logInfo("Found project" + code.toString)))
          .mapError(e => new IOException(s"Could not find project for shortcode $shortcode $e"))
      )
      .flatMap(projectPath =>
        findJpxFiles(projectPath)
          .flatMap(filterWithAssetId)
          .flatMap(filterWithoutOriginal)
          .mapZIO {
            case (assetId, jpxPath) =>
              val originalPath = getOriginalPath(assetId, jpxPath)
              ZIO.logInfo(s"Creating $originalPath for $jpxPath") *>
                SipiClient
                  .transcodeImageFile(fileIn = jpxPath, fileOut = originalPath, outputFormat = targetFormt)
                  .map(sipiOut => (assetId, jpxPath, originalPath, sipiOut))
          }
      )

  private def getOriginalPath(assetId: AssetId, jpxPath: Path) =
    jpxPath.parent.map(_ / s"$assetId.${targetFormt.extension}.orig").orNull

  private def findJpxFiles(projectPath: Path): ZStream[Any, Throwable, Path] =
    Files
      .walk(projectPath)
      .filterZIO(p =>
        Files.isRegularFile(p) && Files.isHidden(p).negate && ZIO.succeed(p.filename.toString.endsWith(".jpx"))
      )

  private def filterWithAssetId(jpxPath: Path): ZStream[Any, Throwable, (AssetId, Path)] =
    AssetId.makeFromPath(jpxPath) match {
      case Some(assetId) => ZStream.succeed((assetId, jpxPath))
      case None          => ZStream.logInfo(s"Not an assetId: $jpxPath") *> ZStream.empty
    }

  private def filterWithoutOriginal(assetIdAndJpxPath: (AssetId, Path)): ZStream[Any, Throwable, (AssetId, Path)] = {
    val (assetId, jpxPath) = assetIdAndJpxPath
    val originalPath: Path = getOriginalPath(assetId, jpxPath)
    ZStream
      .fromZIO(Files.exists(originalPath))
      .flatMap {
        case true  => ZStream.logInfo(s"Original for $jpxPath present: $originalPath") *> ZStream.empty
        case false => ZStream.logInfo(s"Original for $jpxPath not present") *> ZStream.succeed(assetIdAndJpxPath)
      }
  }
}

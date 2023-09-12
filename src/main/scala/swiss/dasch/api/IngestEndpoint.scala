package swiss.dasch.api

import org.apache.commons.io.FilenameUtils
import swiss.dasch.api.ApiPathCodecSegments.{ projects, shortcodePathVar }
import swiss.dasch.api.ListProjectsEndpoint.ProjectResponse
import swiss.dasch.domain.SipiImageFormat.Jpx
import swiss.dasch.domain.*
import zio.*
import zio.http.Status
import zio.http.codec.HttpCodec
import zio.http.endpoint.Endpoint
import zio.nio.file.{ Files, Path }

import java.nio.file.StandardOpenOption

object IngestEndpoint {

  private val endpoint = Endpoint
    .post(projects / shortcodePathVar / "bulk-ingest")
    .out[ProjectResponse]
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  private val route = endpoint.implement(shortcode =>
    ApiStringConverters.fromPathVarToProjectShortcode(shortcode).flatMap { code =>
      ZIO.logInfo(s"Starting bulk ingest for project $code") *>
        BulkIngestService.startBulkIngest(code).forkDaemon *>
        ZIO.succeed(ProjectResponse(code))
    }
  )

  val app = route.toApp
}

trait BulkIngestService {

  def startBulkIngest(shortcode: ProjectShortcode): Task[Int]
}

object BulkIngestService {
  def startBulkIngest(shortcode: ProjectShortcode): ZIO[BulkIngestService, Throwable, Int] =
    ZIO.serviceWithZIO[BulkIngestService](_.startBulkIngest(shortcode))
}

final case class BulkIngestServiceLive(
    storage: StorageService,
    sipiClient: SipiClient,
    assetInfo: AssetInfoService,
  ) extends BulkIngestService {

  override def startBulkIngest(shortcode: ProjectShortcode): Task[Int] =
    for {
      importDir  <- storage.getTempDirectory().map(_ / "import" / shortcode.value)
      mappingFile = importDir / "mapping.csv"
      _          <- Files.createFile(mappingFile)
      _          <- Files.writeLines(mappingFile, List("original,derivative\n"))
      sum        <- StorageService
                      .findInPath(importDir, FileFilters.isImage)
                      .mapZIOPar(8)(ingestSingleImage(_, shortcode, mappingFile))
                      .runSum
    } yield sum

  private def ingestSingleImage(
      file: Path,
      shortcode: ProjectShortcode,
      mappingFile: Path,
    ): Task[Int] =
    for {
      assetId <- AssetId.makeNew

      // ensure asset dir exists
      assetDir <- storage.getAssetDirectory(Asset(assetId, shortcode))
      _        <- Files.createDirectories(assetDir)

      // copy original to asset dir
      originalFile = assetDir / s"${assetId.toString}${FilenameUtils.getExtension(file.filename.toString)}.orig"
      _           <- Files.copy(file, originalFile)

      // transcode to derivative
      derivativeFile = assetDir / s"$assetId.${Jpx.extension}"
      _             <- sipiClient.transcodeImageFile(originalFile, derivativeFile, Jpx)

      // create asset info
      originalFilename = file.filename.toString
      _               <- assetInfo.createAssetInfo(Asset(assetId, shortcode), originalFilename)

      // update mapping
      _ <- Files.writeLines(
             mappingFile,
             List(s"$originalFilename,${derivativeFile.filename}\n"),
             openOptions = Set(StandardOpenOption.APPEND),
           )

      // cleanup
      _ <- Files.delete(file)
    } yield 1

}

object BulkIngestServiceLive {
  val layer = ZLayer.fromFunction(BulkIngestServiceLive.apply _)
}

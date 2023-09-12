package swiss.dasch.api

import zio.http.endpoint.Endpoint
import swiss.dasch.api.ApiPathCodecSegments.projects
import swiss.dasch.api.ApiPathCodecSegments.shortcodePathVar
import zio.http.Status
import zio.*
import zio.nio.file.Files
import swiss.dasch.api.ListProjectsEndpoint.ProjectResponse
import swiss.dasch.domain.ProjectShortcode
import zio.http.codec.HttpCodec
import swiss.dasch.domain.StorageService
import swiss.dasch.domain.FileFilters
import zio.nio.file.Path
import swiss.dasch.domain.AssetId

object IngestEndpoint {

  val endpoint = Endpoint
    .post(projects / shortcodePathVar / "bulk-ingest")
    .out[ProjectResponse]
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  val route = endpoint.implement(shortcode =>
    ApiStringConverters.fromPathVarToProjectShortcode(shortcode).flatMap { code =>
      BulkIngestService
        .startBulkIngest(code)
        .mapError(ApiProblem.internalError(_))
        .as(ProjectResponse(code))
    }
  )

}

trait BulkIngestService {

  def startBulkIngest(shortcode: ProjectShortcode): Task[Unit]
}

object BulkIngestService {
  def startBulkIngest(shortcode: ProjectShortcode): ZIO[BulkIngestService, Throwable, Unit] =
    ZIO.serviceWithZIO[BulkIngestService](_.startBulkIngest(shortcode))
}

final case class BulkIngestServiceLive(storage: StorageService) extends BulkIngestService {

  override def startBulkIngest(shortcode: ProjectShortcode): Task[Unit] =
    for {
      importDir  <- storage.getTempDirectory().map(_ / "import" / shortcode.value)
      imageFiles <- StorageService.findInPath(importDir, FileFilters.isImage).runCollect
      projectDir <- createProjectDirectory(shortcode)
      _          <- ZIO.foreachDiscard(imageFiles)(ingestSingleImage(_, projectDir))
    } yield ()

  private def createProjectDirectory(code: ProjectShortcode): Task[Path] =
    for {
      projectDir <- storage.getProjectDirectory(code)
      _          <- ZIO.whenZIO(Files.isDirectory(projectDir).negate)(Files.createDirectories(projectDir))
    } yield projectDir

  private def ingestSingleImage(file: Path, projectDir: Path): Task[Unit] =
    for {
      _                <- ZIO.unit
      assetId: AssetId <- ZIO.succeed(???)
      _                <- copyOriginal
      _                <- createJpeg2000
      _                <- createInfoFile
      _                <- removeTempFile
    } yield ()

}

object BulkIngestServiceLive {
  val layer = ZLayer.fromFunction(BulkIngestServiceLive.apply _)
}

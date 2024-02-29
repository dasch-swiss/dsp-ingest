package swiss.dasch.api

import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.domain.*
import zio.stream.ZStream
import zio.{ZIO, ZLayer}

final class ReportEndpointsHandler(
  reportEndpoints: ReportEndpoints,
  reportService: ReportService,
  projectService: ProjectService
) {

  private val postAssetOverviewReportHandler: ZServerEndpoint[Any, Any] =
    reportEndpoints.postAssetOverviewReport.serverLogic(_ =>
      _ => createAssetOverReports.forkDaemon.logError.as("work in progress")
    )

  private val createAssetOverReports: ZIO[Any, Throwable, Unit] =
    for {
      reports <- ZStream
                   .fromIterableZIO(projectService.listAllProjects().map(_.map(_.shortcode)))
                   .mapZIO(prj => reportService.assetsOverviewReport(prj))
                   .runCollect
                   .map(_.flatten)
      report <- reportService.saveReport("asset-overview", reports).flatMap(_.toAbsolutePath)
      _      <- ZIO.logInfo(s"Created $report asset overview reports")
    } yield ()

  val endpoints: List[ZServerEndpoint[Any, Any]] =
    List(postAssetOverviewReportHandler)
}

object ReportEndpointsHandler {

  val layer = ZLayer.derive[ReportEndpointsHandler]
}

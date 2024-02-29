package swiss.dasch.api

import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.domain.*
import zio.{ZIO, ZLayer}

final class ReportEndpointsHandler(reportEndpoints: ReportEndpoints, reportService: ReportService) {

  private val foo: ZServerEndpoint[Any, Any] = reportEndpoints.postAssetOverviewReport.serverLogic(_ =>
    _ =>
      reportService.assetsOverviewReport
        .tap(report => ZIO.logInfo(report.toString))
        .forkDaemon
        .logError
        .as("work in progress")
  )

  val endpoints: List[ZServerEndpoint[Any, Any]] =
    List(foo)
}

object ReportEndpointsHandler {

  val layer = ZLayer.derive[ReportEndpointsHandler]
}

package swiss.dasch.api

import sttp.model.StatusCode
import sttp.tapir.ztapir.*

final case class ReportEndpoints(baseEndpoints: BaseEndpoints) {

  private val report      = "report"
  private val maintenance = "maintenance"

  private[api] val postAssetOverviewReport =
    baseEndpoints.secureEndpoint.post
      .in(report / "asset-overview")
      .out(stringBody)
      .out(statusCode(StatusCode.Accepted))
      .tag(maintenance)

  val endpoints = List(postAssetOverviewReport)
}

object ReportEndpoints {
  val layer = zio.ZLayer.derive[ReportEndpoints]
}

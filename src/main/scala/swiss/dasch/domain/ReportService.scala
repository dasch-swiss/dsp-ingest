package swiss.dasch.domain

import zio.*

final case class Report(map: Map[AssetInfo, Chunk[ChecksumResult]])
trait ReportService  {
  def verificationReport(projectShortcode: ProjectShortcode): Task[Report]
}
object ReportService {
  def verificationReport(projectShortcode: ProjectShortcode): RIO[ReportService, Report] =
    ZIO.serviceWithZIO[ReportService](_.verificationReport(projectShortcode))
}

final case class ReportServiceLive(projectService: ProjectService, assetService: AssetService) extends ReportService {
  override def verificationReport(projectShortcode: ProjectShortcode): Task[Report] =
    for {
      infos   <- projectService.findAssetInfosOfProject(projectShortcode)
      results <- ZIO.foreach(infos)(info => assetService.verifyChecksum(info).map((info, _)))
    } yield Report(results.toMap)
}
object ReportServiceLive {
  val layer = ZLayer.fromFunction(ReportServiceLive.apply _)
}

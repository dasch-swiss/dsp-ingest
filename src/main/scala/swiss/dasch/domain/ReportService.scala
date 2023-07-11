/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

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
    projectService
      .findAssetInfosOfProject(projectShortcode)
      .mapZIO(info => assetService.verifyChecksum(info).map((info, _)))
      .runCollect
      .map(it => Report(it.toMap))
}
object ReportServiceLive {
  val layer = ZLayer.fromFunction(ReportServiceLive.apply _)
}

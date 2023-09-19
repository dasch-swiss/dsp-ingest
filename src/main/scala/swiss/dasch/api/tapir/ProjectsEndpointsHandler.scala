/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.tapir

import sttp.model.headers.ContentRange
import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.api.ApiProblem
import swiss.dasch.api.ListProjectsEndpoint.ProjectResponse
import swiss.dasch.api.ReportEndpoint.AssetCheckResultResponse
import swiss.dasch.domain.{ ProjectService, ReportService }
import zio.ZLayer

final case class ProjectsEndpointsHandler(
    projectEndpoints: ProjectsEndpoints,
    projectService: ProjectService,
    reportService: ReportService,
  ) {

  val getProjectsEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints
    .getProjectsEndpoint
    .serverLogic(_ =>
      _ =>
        projectService
          .listAllProjects()
          .mapBoth(
            _ => ApiProblem.InternalServerError("Something went wrong"),
            list =>
              (list.map(ProjectResponse.make), ContentRange("items", Some(0, list.size), Some(list.size)).toString),
          )
    )

  val getProjectByShortcodeEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints
    .getProjectByShortcodeEndpoint
    .serverLogic(_ =>
      shortcode =>
        projectService
          .findProject(shortcode)
          .some
          .mapBoth(
            {
              case None    => ApiProblem.NotFound(shortcode)
              case Some(_) => ApiProblem.InternalServerError("Something went wrong")
            },
            _ => ProjectResponse.make(shortcode),
          )
    )

  val getProjectChecksumReportEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints
    .getProjectsChecksumReport
    .serverLogic(_ =>
      shortcode =>
        reportService
          .checksumReport(shortcode)
          .some
          .mapBoth(
            {
              case None    => ApiProblem.NotFound(shortcode)
              case Some(e) => ApiProblem.InternalServerError(e)
            },
            AssetCheckResultResponse.make,
          )
    )

  val endpoints: List[ZServerEndpoint[Any, Any]] =
    List(getProjectsEndpoint, getProjectByShortcodeEndpoint, getProjectChecksumReportEndpoint)
}

object ProjectsEndpointsHandler {

  val layer = ZLayer.fromFunction(ProjectsEndpointsHandler.apply _)

}

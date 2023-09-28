/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.tapir

import sttp.model.headers.ContentRange
import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.api.ApiProblem
import swiss.dasch.api.ApiProblem.*
import swiss.dasch.api.tapir.ProjectsEndpointsResponses.{ AssetCheckResultResponse, ProjectResponse }
import swiss.dasch.domain.{ BulkIngestService, ProjectService, ProjectShortcode, ReportService }
import zio.stream.ZStream
import zio.{ ZIO, ZLayer }

final case class ProjectsEndpointsHandler(
    projectEndpoints: ProjectsEndpoints,
    projectService: ProjectService,
    reportService: ReportService,
    bulkIngestService: BulkIngestService,
  ) extends HandlerFunctions {

  val getProjectsEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints
    .getProjectsEndpoint
    .serverLogic(_ =>
      _ =>
        projectService
          .listAllProjects()
          .mapBoth(
            InternalServerError(_),
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
            projectNotFoundOrServerError(_, shortcode),
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
            projectNotFoundOrServerError(_, shortcode),
            AssetCheckResultResponse.make,
          )
    )

  val postBulkIngestEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints
    .postBulkIngest
    .serverLogic(_ =>
      code => bulkIngestService.startBulkIngest(code).logError.forkDaemon *> ZIO.succeed(ProjectResponse.make(code))
    )

  val postExportEndpoint: ZServerEndpoint[Any, sttp.capabilities.zio.ZioStreams] = projectEndpoints
    .postExport
    .serverLogic(_ =>
      shortcode =>
        for {
          response <- projectService
                        .zipProject(shortcode)
                        .some
                        .mapBoth(
                          {
                            case Some(err) => InternalServerError(err)
                            case _         => NotFound(shortcode)
                          },
                          path =>
                            (
                              s"attachment; filename=export-$shortcode.zip",
                              "application/zip",
                              ZStream.fromFile(path.toFile).orDie,
                            ),
                        )
        } yield response
    )

  val endpoints: List[ZServerEndpoint[Any, sttp.capabilities.zio.ZioStreams]] =
    List(
      getProjectsEndpoint,
      getProjectByShortcodeEndpoint,
      getProjectChecksumReportEndpoint,
      postBulkIngestEndpoint,
      postExportEndpoint,
    )
}

object ProjectsEndpointsHandler {
  val layer = ZLayer.fromFunction(ProjectsEndpointsHandler.apply _)
}

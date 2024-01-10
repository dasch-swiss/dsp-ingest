/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.domain.*
import zio.{Chunk, ZIO, ZLayer}

final case class MaintenanceEndpointsHandler(
  maintenanceEndpoints: MaintenanceEndpoints,
  maintenanceActions: MaintenanceActions,
  projectService: ProjectService,
  fileChecksumService: FileChecksumService,
  sipiClient: SipiClient,
  imageService: StillImageService
) extends HandlerFunctions {

  private val postMaintenanceEndpoint: ZServerEndpoint[Any, Any] = maintenanceEndpoints.postMaintenanceActionEndpoint
    .serverLogic(_ => { case (action, shortcodes) =>
      for {
        paths <-
          ZIO
            .ifZIO(ZIO.succeed(shortcodes.isEmpty))(
              projectService.listAllProjects(),
              ZIO.succeed(Chunk.fromIterable(shortcodes))
            )
            .flatMap(projectService.findProjects)
            .mapError(ApiProblem.InternalServerError(_))
        _ <- ZIO.logInfo(s"Maintenance endpoint called $action, $shortcodes, $paths")
      } yield "work in progress"
    })

  val applyTopLeftCorrectionEndpoint: ZServerEndpoint[Any, Any] =
    maintenanceEndpoints.applyTopLeftCorrectionEndpoint.serverLogic { _ => shortcode =>
      projectService
        .findProject(shortcode)
        .some
        .flatMap(path =>
          maintenanceActions
            .applyTopLeftCorrections(path.value)
            .tap(count => ZIO.logInfo(s"Created $count originals for $path"))
            .logError
            .forkDaemon
        )
        .mapError(projectNotFoundOrServerError(_, shortcode))
        .unit
    }

  val createOriginalsEndpoint: ZServerEndpoint[Any, Any] = maintenanceEndpoints.createOriginalsEndpoint
    .serverLogic(_ =>
      (shortcode, mappings) =>
        projectService
          .findProject(shortcode)
          .some
          .flatMap(path =>
            maintenanceActions
              .createOriginals(path.value, mappings.map(e => e.internalFilename -> e.originalFilename).toMap)
              .tap(count => ZIO.logInfo(s"Created $count originals for ${path.value}"))
              .logError
              .forkDaemon
          )
          .mapError(projectNotFoundOrServerError(_, shortcode))
          .unit
    )

  val needsOriginalsEndpoint: ZServerEndpoint[Any, Any] = maintenanceEndpoints.needsOriginalsEndpoint
    .serverLogic(_ =>
      imagesOnlyMaybe =>
        maintenanceActions
          .createNeedsOriginalsReport(imagesOnlyMaybe.getOrElse(true))
          .forkDaemon
          .logError
          .as("work in progress")
    )

  val needsTopLeftCorrectionEndpoint: ZServerEndpoint[Any, Any] = maintenanceEndpoints.needsTopLeftCorrectionEndpoint
    .serverLogic(_ =>
      _ =>
        maintenanceActions
          .createNeedsTopLeftCorrectionReport()
          .forkDaemon
          .logError
          .as("work in progress")
    )

  val wasTopLeftCorrectionAppliedEndpoint: ZServerEndpoint[Any, Any] =
    maintenanceEndpoints.wasTopLeftCorrectionAppliedEndpoint
      .serverLogic(_ =>
        _ =>
          maintenanceActions
            .createWasTopLeftCorrectionAppliedReport()
            .forkDaemon
            .logError
            .as("work in progress")
      )

  val extractImageMetadataAndAddToInfoFileEndpoint: ZServerEndpoint[Any, Any] =
    maintenanceEndpoints.extractImageMetadataAndAddToInfoFileEndpoint
      .serverLogic(_ =>
        _ =>
          maintenanceActions
            .extractImageMetadataAndAddToInfoFile()
            .forkDaemon
            .logError
            .as("work in progress")
      )

  val endpoints: List[ZServerEndpoint[Any, Any]] =
    List(
      postMaintenanceEndpoint,
      applyTopLeftCorrectionEndpoint,
      createOriginalsEndpoint,
      needsOriginalsEndpoint,
      needsTopLeftCorrectionEndpoint,
      wasTopLeftCorrectionAppliedEndpoint,
      extractImageMetadataAndAddToInfoFileEndpoint
    )
}

object MaintenanceEndpointsHandler {
  val layer = ZLayer.derive[MaintenanceEndpointsHandler]
}

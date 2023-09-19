/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.tapir

import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.api.ApiProblem
import swiss.dasch.domain.*
import zio.{ ZIO, ZLayer }

final case class MaintenanceEndpointsHandler(
    maintenanceEndpoints: MaintenanceEndpoints,
    projectService: ProjectService,
    fileChecksumService: FileChecksumService,
    sipiClient: SipiClient,
    imageService: ImageService,
  ) {

  val applyTopLeftCorrectionEndpoint: ZServerEndpoint[Any, Any] = maintenanceEndpoints
    .applyTopLeftCorrectionEndpoint
    .serverLogic { _ => shortcode =>
      projectService
        .findProject(shortcode)
        .some
        .flatMap(path =>
          MaintenanceActions
            .applyTopLeftCorrections(path)
            .provide(ZLayer.succeed(imageService))
            .tap(count => ZIO.logInfo(s"Created $count originals for $path"))
            .logError
            .forkDaemon
        )
        .unit
        .mapError {
          case None    => ApiProblem.NotFound(shortcode)
          case Some(e) => ApiProblem.InternalServerError(e)
        }
    }

  val createOriginalsEndpoint: ZServerEndpoint[Any, Any] = maintenanceEndpoints
    .createOriginalsEndpoint
    .serverLogic(_ =>
      (shortcode, mappings) =>
        projectService
          .findProject(shortcode)
          .some
          .flatMap(path =>
            MaintenanceActions
              .createOriginals(path, mappings.map(e => e.internalFilename -> e.originalFilename).toMap)
              .provide(ZLayer.succeed(sipiClient), ZLayer.succeed(fileChecksumService))
              .tap(count => ZIO.logInfo(s"Created $count originals for $path"))
              .logError
              .forkDaemon
          )
          .unit
          .mapError {
            case None    => ApiProblem.NotFound(shortcode)
            case Some(e) => ApiProblem.InternalServerError(e)
          }
    )

  val endpoints: List[ZServerEndpoint[Any, Any]] = List(applyTopLeftCorrectionEndpoint, createOriginalsEndpoint)
}

object MaintenanceEndpointsHandler {
  val layer = ZLayer.fromFunction(MaintenanceEndpointsHandler.apply _)
}

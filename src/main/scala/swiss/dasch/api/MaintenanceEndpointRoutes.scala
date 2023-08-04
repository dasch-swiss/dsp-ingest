package swiss.dasch.api

import swiss.dasch.api.ListProjectsEndpoint.ProjectResponse
import swiss.dasch.api.MaintenanceEndpoint.*
import swiss.dasch.domain.{ImageService, MaintenanceActions, ProjectService, StorageService}
import zio.nio.file
import zio.nio.file.Files
import zio.{Chunk, ZIO}

object MaintenanceEndpointRoutes {

  private def getProjectPath(shortcodeStr: String): ZIO[ProjectService, ApiProblem, file.Path] =
    ApiStringConverters
      .fromPathVarToProjectShortcode(shortcodeStr)
      .flatMap(code =>
        ProjectService.findProject(code).some.mapError {
          case Some(e) => ApiProblem.internalError(e)
          case _       => ApiProblem.projectNotFound(code)
        }
      )

  private val createOriginalsRoute =
    createOriginalsEndpoint.implement {
      case (shortCodeStr: String, mapping: Chunk[MappingEntry]) =>
        for {
          projectPath <- getProjectPath(shortCodeStr)
          _           <- ZIO.logInfo(s"Creating originals for $projectPath")
          _           <- MaintenanceActions
                           .createOriginals(projectPath, mapping.map(e => e.internalFilename -> e.originalFilename).toMap)
                           .tap(count => ZIO.logInfo(s"Created $count originals for $projectPath"))
                           .logError
                           .forkDaemon
        } yield "work in progress"
    }

  private val needsTopLeftCorrectionRoute =
    needsTopLeftCorrectionEndpoint.implement(_ =>
      (for {
        _                 <- ZIO.logInfo(s"Checking for top left correction")
        assetDir          <- StorageService.getAssetDirectory()
        tmpDir            <- StorageService.getTempDirectory()
        imageService      <- ZIO.service[ImageService]
        projectShortcodes <- ProjectService.listAllProjects()
        _                 <-
          ZIO
            .foreach(projectShortcodes)(shortcode =>
              Files
                .walk(assetDir / shortcode.toString)
                .mapZIOPar(8)(imageService.needsTopLeftCorrection)
                .filter(identity)
                .runHead
                .map(_.map(_ => ProjectResponse.make(shortcode)))
            )
            .map(_.flatten)
            .flatMap(
              Files.createDirectories(tmpDir / "reports") *>
                Files.deleteIfExists(tmpDir / "reports" / "needsTopLeftCorrection.json") *>
                Files.createFile(tmpDir / "reports" / "needsTopLeftCorrection.json") *>
                StorageService.saveJsonFile(tmpDir / "reports" / "needsTopLeftCorrection.json", _)
            )
            .logError
            .forkDaemon
      } yield "work in progress")
        .logError
        .mapError(ApiProblem.internalError)
    )

  private val applyTopLeftCorrectionRoute =
    applyTopLeftCorrectionEndpoint.implement(shortcodeStr =>
      for {
        projectPath <- getProjectPath(shortcodeStr)
        _           <- ZIO.logInfo(s"Creating originals for $projectPath")
        _           <- MaintenanceActions
                         .applyTopLeftCorrections(projectPath)
                         .tap(count => ZIO.logInfo(s"Corrected $count top left images for $projectPath"))
                         .logError
                         .forkDaemon
      } yield "work in progress"
    )

  val app = (createOriginalsRoute ++ needsTopLeftCorrectionRoute ++ applyTopLeftCorrectionRoute).toApp
}

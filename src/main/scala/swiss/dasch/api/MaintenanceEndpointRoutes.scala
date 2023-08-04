package swiss.dasch.api

import swiss.dasch.api.ListProjectsEndpoint.ProjectResponse
import swiss.dasch.api.MaintenanceEndpoint.*
import swiss.dasch.domain.*
import zio.nio.file
import zio.nio.file.Files
import zio.{ Chunk, IO, ZIO }

import java.io.IOException

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

  private val needsOriginalsRoute = needsOriginalsEndpoint.implement(_ =>
    (for {
      _                 <- ZIO.logInfo(s"Checking for originals")
      assetDir          <- StorageService.getAssetDirectory()
      tmpDir            <- StorageService.getTempDirectory()
      projectShortcodes <- ProjectService.listAllProjects()
      _                 <- ZIO
                             .foreach(projectShortcodes)(shortcode =>
                               Files
                                 .walk(assetDir / shortcode.toString)
                                 .mapZIOPar(8)(originalNotPresent)
                                 .filter(identity)
                                 .as(ProjectResponse.make(shortcode))
                                 .runHead
                             )
                             .map(_.flatten)
                             .flatMap(
                               Files.createDirectories(tmpDir / "reports") *>
                                 Files.deleteIfExists(tmpDir / "reports" / "needsOriginals.json") *>
                                 Files.createFile(tmpDir / "reports" / "needsOriginals.json") *>
                                 StorageService.saveJsonFile(tmpDir / "reports" / "needsOriginals.json", _)
                             )
                             .zipLeft(ZIO.logInfo(s"Created needsOriginals.json"))
                             .logError
                             .forkDaemon

    } yield "work in progress")
      .logError
      .mapError(it => ApiProblem.internalError(it))
  )

  private def originalNotPresent(path: file.Path): IO[IOException, Boolean] = {
    val assetId = AssetId.makeFromPath(path).map(_.toString).getOrElse("unknown-asset-id")
    FileFilters.isImage(path) &&
    Files
      .list(path.parent.orNull)
      .map(_.filename.toString)
      .filter(name => name.endsWith(".orig") && name.startsWith(assetId))
      .runHead
      .map(_.isEmpty)
  }

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
            .zipLeft(ZIO.logInfo(s"Created needsTopLeftCorrection.json"))
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

  val app = (needsOriginalsRoute ++
    createOriginalsRoute ++
    needsTopLeftCorrectionRoute ++
    applyTopLeftCorrectionRoute).toApp
}

package swiss.dasch.api

import swiss.dasch.api.ListProjectsEndpoint.ProjectResponse
import swiss.dasch.api.MaintenanceEndpoint.*
import swiss.dasch.domain.*
import zio.nio.file
import zio.nio.file.Files
import zio.{ Chunk, IO, ZIO }

import java.io.IOException
import zio.json.JsonEncoder

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

  private def saveReport[A](
      tmpDir: file.Path,
      name: String,
      report: A,
    )(implicit encoder: JsonEncoder[A]
    ): ZIO[StorageService, Throwable, Unit] =
    Files.createDirectories(tmpDir / "reports") *>
      Files.deleteIfExists(tmpDir / "reports" / s"$name.json") *>
      Files.createFile(tmpDir / "reports" / s"$name.json") *>
      StorageService.saveJsonFile(tmpDir / "reports" / s"$name.json", report)

  private val needsOriginalsRoute = needsOriginalsEndpoint.implement(imagesOnly =>
    (
      for {
        _                 <- ZIO.logInfo(s"Checking for originals")
        assetDir          <- StorageService.getAssetDirectory()
        tmpDir            <- StorageService.getTempDirectory()
        projectShortcodes <- ProjectService.listAllProjects()
        _                 <- ZIO
                               .foreach(projectShortcodes)(shortcode =>
                                 Files
                                   .walk(assetDir / shortcode.toString)
                                   .mapZIOPar(8)(originalNotPresent(imagesOnly))
                                   .filter(identity)
                                   .as(ProjectResponse.make(shortcode))
                                   .runHead
                               )
                               .map(_.flatten)
                               .flatMap(saveReport(tmpDir, "needsOriginals", _))
                               .zipLeft(ZIO.logInfo(s"Created needsOriginals.json"))
                               .logError
                               .forkDaemon
      } yield "work in progress"
    ).logError.mapError(ApiProblem.internalError)
  )

  private def originalNotPresent(imagesOnly: Boolean)(path: file.Path): IO[IOException, Boolean] = {
    val assetId = AssetId.makeFromPath(path).map(_.toString).getOrElse("unknown-asset-id")
    (ZIO.succeed(imagesOnly).negate || FileFilters.isImage(path)) &&
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
      (
        for {
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
              .flatMap(saveReport(tmpDir, "needsTopLeftCorrection", _))
              .zipLeft(ZIO.logInfo(s"Created needsTopLeftCorrection.json"))
              .logError
              .forkDaemon
        } yield "work in progress"
      ).logError.mapError(ApiProblem.internalError)
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

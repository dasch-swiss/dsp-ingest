package swiss.dasch.domain

import zio.*
import zio.nio.file.*

import java.util.zip.ZipFile

sealed trait ImportFailed
case class ImportIoError(e: Throwable) extends ImportFailed
case object InputFileEmpty             extends ImportFailed
case object InputFileInvalid           extends ImportFailed

trait ImportService  {
  def importZipFile(shortcode: ProjectShortcode, tempFile: Path): IO[ImportFailed, Unit]
}
object ImportService {
  def importZipFile(shortcode: ProjectShortcode, tempFile: Path): ZIO[ImportService, ImportFailed, Unit] =
    ZIO.serviceWithZIO[ImportService](_.importZipFile(shortcode, tempFile))
}

final case class ImportServiceLive(projectService: ProjectService, storageService: StorageService)
    extends ImportService {

  override def importZipFile(shortcode: ProjectShortcode, uploadedFile: Path): IO[ImportFailed, Unit] =
    for {
      _ <- validateUploadedFile(uploadedFile)
      _ <- importProject(shortcode, uploadedFile)
             .logError(s"Error while importing project $shortcode")
             .mapError(ImportIoError(_))
    } yield ()

  private def validateUploadedFile(uploadedFolder: Path): IO[ImportFailed, Unit] =
    for {
      _ <- checkIsNotEmptyFile(uploadedFolder)
      _ <- checkIsZipFile(uploadedFolder)
    } yield ()
  private def checkIsNotEmptyFile(path: Path): IO[ImportFailed, Unit]            =
    ZIO.fail(InputFileEmpty).whenZIO(Files.size(path).mapBoth(ImportIoError(_), _ == 0)).unit
  private def checkIsZipFile(path: Path): IO[InputFileInvalid.type, Unit]        = ZIO.scoped {
    ZIO.fromAutoCloseable(ZIO.attemptBlockingIO(new ZipFile(path.toFile))).orElseFail(InputFileInvalid).unit
  }
  private def importProject(shortcode: ProjectShortcode, zipFile: Path): IO[Throwable, Unit] =
    storageService.getProjectDirectory(shortcode).flatMap { projectPath =>
      ZIO.logInfo(s"Importing project $shortcode") *>
        projectService.deleteProject(shortcode) *>
        Files.createDirectories(projectPath) *>
        unzipAndValidate(zipFile, projectPath) *>
        ZIO.logInfo(s"Importing project $shortcode was successful")
    }

  private def unzipAndValidate(zipFile: Path, projectPath: Path) =
    ZipUtility.unzipFile(zipFile, projectPath) // TODO: *> validateChecksumsProject(projectPath)

}
object ImportServiceLive {
  val layer: ZLayer[ProjectService with StorageService, Nothing, ImportService] = ZLayer.fromFunction(ImportServiceLive.apply _)
}

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

final case class ImportServiceLive(projectService: ProjectService) extends ImportService {

  override def importZipFile(shortcode: ProjectShortcode, tempFile: Path): IO[ImportFailed, Unit] =
    for {
      _                 <- validateInputFile(tempFile)
      importFileErrorMsg = s"Error while importing project $shortcode"
      _                 <- projectService
                             .importProject(shortcode, tempFile)
                             .logError(importFileErrorMsg)
                             .mapError(ImportIoError(_))
    } yield ()

  private def validateInputFile(tempFile: Path): ZIO[Any, ImportFailed, Unit] =
    (for {
      _ <- ZIO.fail(InputFileEmpty).whenZIO(Files.size(tempFile).mapBoth(ImportIoError(_), _ == 0))
      _ <- ZIO.scoped {
             ZIO.fromAutoCloseable(ZIO.attemptBlockingIO(new ZipFile(tempFile.toFile))).orElseFail(InputFileInvalid)
           }
    } yield ()).tapError(_ => Files.deleteIfExists(tempFile).mapError(ImportIoError(_)))

}
object ImportServiceLive {
  val layer: ZLayer[ProjectService, Nothing, ImportService] = ZLayer.fromFunction(ImportServiceLive.apply _)
}

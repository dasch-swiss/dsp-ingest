/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.json.{ DeriveJsonCodec, DeriveJsonDecoder, DeriveJsonEncoder, JsonCodec, JsonDecoder, JsonEncoder }
import zio.nio.file.Files.{ delete, deleteIfExists, isDirectory, newDirectoryStream }
import zio.nio.file.{ Files, Path }
import zio.stream.{ ZSink, ZStream }

import java.io.IOException

opaque type ProjectShortcode = String Refined MatchesRegex["""^\p{XDigit}{4,4}$"""]
type IiifPrefix              = ProjectShortcode

object ProjectShortcode {
  def make(shortcode: String): Either[String, ProjectShortcode] = refineV(shortcode.toUpperCase)
}

trait ProjectService {
  def listAllProjects(): IO[IOException, Chunk[ProjectShortcode]]
  def findProject(shortcode: ProjectShortcode): IO[IOException, Option[Path]]
  def zipProject(shortcode: ProjectShortcode): Task[Option[Path]]
  def importProject(shortcode: ProjectShortcode, zipFile: Path): IO[Throwable, Unit]
}

object ProjectService {
  def listAllProjects(): ZIO[ProjectService, IOException, Chunk[ProjectShortcode]]                    =
    ZIO.serviceWithZIO[ProjectService](_.listAllProjects())
  def findProject(shortcode: ProjectShortcode): ZIO[ProjectService, IOException, Option[Path]]        =
    ZIO.serviceWithZIO[ProjectService](_.findProject(shortcode))
  def zipProject(shortcode: ProjectShortcode): ZIO[ProjectService, Throwable, Option[Path]]           =
    ZIO.serviceWithZIO[ProjectService](_.zipProject(shortcode))
  def importProject(shortcode: ProjectShortcode, zipFile: Path): ZIO[ProjectService, Throwable, Unit] =
    ZIO.serviceWithZIO[ProjectService](_.importProject(shortcode, zipFile))
}

final case class ProjectServiceLive(storage: StorageService) extends ProjectService {

  override def listAllProjects(): IO[IOException, Chunk[ProjectShortcode]] =
    ZStream
      .fromZIO(storage.getAssetDirectory())
      .flatMap(newDirectoryStream(_))
      .filterZIO(directoryContainsNonHiddenRegularFile)
      .runCollect
      .map(toProjectShortcodes)
  private def directoryContainsNonHiddenRegularFile(path: Path)            =
    Files.isDirectory(path) &&
      Files
        .walk(path)
        .filterZIO(it => Files.isRegularFile(it) && Files.isHidden(it).negate)
        .runHead
        .map(_.isDefined)

  private val toProjectShortcodes: Chunk[Path] => Chunk[ProjectShortcode] =
    _.map(_.filename.toString).sorted.flatMap(ProjectShortcode.make(_).toOption)

  override def findProject(shortcode: ProjectShortcode): IO[IOException, Option[Path]] = for {
    projectPath <- storage.getProjectDirectory(shortcode)
    projectDir  <- ZIO.whenZIO(Files.isDirectory(projectPath))(ZIO.succeed(projectPath))
  } yield projectDir

  override def zipProject(shortcode: ProjectShortcode): Task[Option[Path]] =
    ZIO.logInfo(s"Zipping project $shortcode") *>
      findProject(shortcode).flatMap(_.map(zipProjectPath).getOrElse(ZIO.none)) <*
      ZIO.logInfo(s"Zipping project $shortcode was successful")

  private def zipProjectPath(projectPath: Path) = for {
    targetFolder <- storage.getTempDirectory().map(_ / "zipped")
    zippedPath   <- ZipUtility.zipFolder(projectPath, targetFolder).map(Some(_))
  } yield zippedPath

  override def importProject(shortcode: ProjectShortcode, zipFile: Path): IO[Throwable, Unit] =
    storage.getProjectDirectory(shortcode).flatMap { projectPath =>
      ZIO.logInfo(s"Importing project $shortcode") *>
        deleteExistingProjectFiles(projectPath) *>
        Files.createDirectories(projectPath) *>
        ZipUtility.unzipFile(zipFile, projectPath) *>
        ZIO.logInfo(s"Importing project $shortcode was successful")
    }

  private def deleteExistingProjectFiles(projectPath: Path): IO[IOException, Long] =
    deleteRecursive(projectPath)
      .whenZIO(Files.exists(projectPath))
      .map(_.getOrElse(0L))
      .tap(count => ZIO.logDebug(s"Deleted $count files in $projectPath"))

  // The zio.nio.file.Files.deleteRecursive function has a bug in 2.0.1
  // https://github.com/zio/zio-nio/pull/588/files <- this PR fixes it
  // This is a workaround until the bug is fixed:
  private def deleteRecursive(path: Path)(implicit trace: Trace): ZIO[Any, IOException, Long] =
    newDirectoryStream(path)
      .mapZIO { p =>
        for {
          deletedInSubDirectory <- deleteRecursive(p).whenZIO(isDirectory(p)).map(_.getOrElse(0L))
          deletedFile           <- deleteIfExists(p).map(if (_) 1 else 0)
        } yield deletedInSubDirectory + deletedFile
      }
      .run(ZSink.sum) <* delete(path)
}

object ProjectServiceLive {
  val layer = ZLayer.fromFunction(ProjectServiceLive.apply _)
}

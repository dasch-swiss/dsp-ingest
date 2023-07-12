/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FileUtils
import zio.*
import zio.nio.file.*
import zio.nio.file.Files.newDirectoryStream
import zio.stream.ZSink

import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

sealed trait ImportFailed
case class IoError(e: Throwable) extends ImportFailed
case object EmptyFile            extends ImportFailed
case object NoZipFile            extends ImportFailed
case object InvalidChecksums     extends ImportFailed

trait ImportService      {
  def importZipFile(shortcode: ProjectShortcode, tempFile: Path): IO[ImportFailed, Unit]
}
object ImportService     {
  def importZipFile(shortcode: ProjectShortcode, tempFile: Path): ZIO[ImportService, ImportFailed, Unit] =
    ZIO.serviceWithZIO[ImportService](_.importZipFile(shortcode, tempFile))
}

final case class ImportServiceLive(
    assetService: AssetService,
    assetInfos: AssetInfoService,
    projectService: ProjectService,
    storageService: StorageService,
  ) extends ImportService {

  override def importZipFile(shortcode: ProjectShortcode, zipFile: Path): IO[ImportFailed, Unit] = ZIO.scoped {
    for {
      unzippedFolder <- validateZipFile(shortcode, zipFile)
      _              <- importProject(shortcode, unzippedFolder)
                          .logError(s"Error while importing project $shortcode")
                          .mapError(IoError(_))
    } yield ()
  }.logError

  private def validateZipFile(shortcode: ProjectShortcode, zipFile: Path): ZIO[Scope, ImportFailed, Path]           =
    for {
      _            <- checkIsNotEmptyFile(zipFile)
      _            <- checkIsZipFile(zipFile)
      unzippedPath <- checkUnzipAndCheckProject(shortcode, zipFile)
    } yield unzippedPath
  private def checkIsNotEmptyFile(zipFile: Path): IO[ImportFailed, Unit]                                            =
    ZIO.fail(EmptyFile).whenZIO(Files.size(zipFile).mapBoth(IoError(_), _ == 0)).unit
  private def checkIsZipFile(zipFile: Path): IO[NoZipFile.type, Unit]                                               = ZIO.scoped {
    ZIO.fromAutoCloseable(ZIO.attemptBlockingIO(new ZipFile(zipFile.toFile))).orElseFail(NoZipFile).unit
  }
  private def checkUnzipAndCheckProject(shortcode: ProjectShortcode, zipFile: Path): ZIO[Scope, ImportFailed, Path] =
    for {
      tempDir <- Files.createTempDirectoryScoped(Some("import"), List.empty).mapError(IoError(_))
      _       <- ZipUtility.unzipFile(zipFile, tempDir).mapError(IoError(_))
      checks  <- assetInfos
                   .findAllInPath(tempDir, shortcode)
                   .mapZIO(assetService.verifyChecksum)
                   .runCollect
                   .mapBoth(IoError(_), _.flatten)
      _       <- ZIO.fail(InvalidChecksums).when(checks.exists(!_.checksumMatches))
    } yield tempDir

  private def importProject(shortcode: ProjectShortcode, unzippedFolder: Path): IO[Throwable, Unit] =
    storageService.getProjectDirectory(shortcode).flatMap { projectPath =>
      ZIO.logInfo(s"Importing project $shortcode") *>
        projectService.deleteProject(shortcode) *>
        moveDirectory(unzippedFolder, projectPath) *>
        ZIO.logInfo(s"Importing project $shortcode was successful")
    }

  private def moveDirectory(sourceDir: Path, targetDir: Path): IO[Throwable, Unit] =
    ZIO.attemptBlockingIO(FileUtils.moveDirectory(sourceDir.toFile, targetDir.toFile))
}
object ImportServiceLive {
  val layer
      : ZLayer[AssetService with AssetInfoService with ProjectService with StorageService, Nothing, ImportService] =
    ZLayer.fromFunction(ImportServiceLive.apply _)
}

/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration.IngestConfig
import zio.nio.file.{Files, Path}
import zio.{IO, Task, ZIO, ZLayer}

import java.io.IOException
import java.nio.file.StandardOpenOption

trait BulkIngestService {

  def startBulkIngest(shortcode: ProjectShortcode): Task[IngestResult]
}

object BulkIngestService {
  def startBulkIngest(shortcode: ProjectShortcode): ZIO[BulkIngestService, Throwable, IngestResult] =
    ZIO.serviceWithZIO[BulkIngestService](_.startBulkIngest(shortcode))
}

case class IngestResult(success: Int = 0, failed: Int = 0) {
  def +(other: IngestResult): IngestResult = IngestResult(success + other.success, failed + other.failed)
}

object IngestResult {
  val success: IngestResult = IngestResult(success = 1)
  val failed: IngestResult  = IngestResult(failed = 1)
}

final case class BulkIngestServiceLive(
  storage: StorageService,
  ingestService: IngestService,
  config: IngestConfig
) extends BulkIngestService {

  override def startBulkIngest(project: ProjectShortcode): Task[IngestResult] =
    for {
      _           <- ZIO.logInfo(s"Starting bulk ingest for project $project")
      importDir   <- storage.getBulkIngestImportFolder(project)
      _           <- ZIO.fail(new IOException(s"Import directory '$importDir' does not exist")).unlessZIO(Files.exists(importDir))
      mappingFile <- storage.createBulkIngestMappingFile(project)
      _           <- ZIO.logInfo(s"Import dir: $importDir, mapping file: $mappingFile")
      total       <- StorageService.findInPath(importDir, FileFilters.isNonHiddenRegularFile).runCount
      sum <- StorageService
               .findInPath(importDir, FileFilters.isSupported)
               .mapZIOPar(config.bulkMaxParallel)(file =>
                 ingestService
                   .ingestFile(file, project)
                   .flatMap(asset => updateMappingCsv(mappingFile, file, asset))
                   .as(IngestResult.success)
                   .logError
                   .catchNonFatalOrDie(e =>
                     ZIO
                       .logError(s"Error ingesting image $file: ${e.getMessage}")
                       .as(IngestResult.failed)
                   )
               )
               .runFold(IngestResult())(_ + _)
      _ <- {
        val countAssets  = sum.success + sum.failed
        val countSuccess = sum.success
        val countFailed  = sum.failed
        ZIO.logInfo(
          s"Finished bulk ingest for project $project. " +
            s"Found $countAssets assets from $total files. " +
            s"Ingested $countSuccess successfully and failed $countFailed assets (See logs above for more details)."
        )
      }
    } yield sum

  private def updateMappingCsv(mappingFile: Path, fileToIngest: Path, asset: Asset): IO[IOException, Unit] =
    ZIO.logInfo(s"Updating mapping file $mappingFile, $asset") *> {
      for {
        importDir                <- storage.getBulkIngestImportFolder(asset.belongsToProject)
        imageToIngestRelativePath = importDir.relativize(fileToIngest)
        _ <- Files.writeLines(
               mappingFile,
               List(s"$imageToIngestRelativePath,${asset.derivative.filename}"),
               openOptions = Set(StandardOpenOption.APPEND)
             )
      } yield ()
    }
}

object BulkIngestServiceLive {
  val layer = ZLayer.derive[BulkIngestServiceLive]
}

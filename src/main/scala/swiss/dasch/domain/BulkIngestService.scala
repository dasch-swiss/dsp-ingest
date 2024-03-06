/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration.IngestConfig
import zio.nio.file.{Files, Path}
import zio.stm.{STM, TMap, TSemaphore}
import zio.{Cause, Duration, IO, Task, UIO, ZIO, ZLayer}

import java.io.IOException
import java.nio.file.StandardOpenOption

case class IngestResult(success: Int = 0, failed: Int = 0) {
  def +(other: IngestResult): IngestResult = IngestResult(success + other.success, failed + other.failed)
}

object IngestResult {
  val success: IngestResult = IngestResult(success = 1)
  val failed: IngestResult  = IngestResult(failed = 1)
}

final case class BulkIngestService(
  storage: StorageService,
  ingestService: IngestService,
  config: IngestConfig,
  semaphoresPerProject: TMap[ProjectShortcode, TSemaphore],
) {

  private def getSemaphoreWithTimeoutFor(key: ProjectShortcode): ZIO[Any, Nothing, Option[TSemaphore]] =
    semaphoresPerProject
      .get(key)
      .flatMap {
        case Some(sem) => STM.succeed(sem)
        case None      => TSemaphore.make(1).tap(sem => semaphoresPerProject.put(key, sem))
      }
      .commit
      .flatMap(s => s.acquire.commit.timeout(Duration.fromMillis(400)).map(_.map(_ => s)))

  def startBulkIngest(project: ProjectShortcode): ZIO[Any, Nothing, Option[Unit]] =
    for {
      acquiredMaybe <- getSemaphoreWithTimeoutFor(project)
      _ <- ZIO
             .fromOption(acquiredMaybe)
             .flatMap(sem => doBulkIngest(project).logError.ensuring(sem.release.commit).forkDaemon)
             .unsome
    } yield acquiredMaybe.map(_ => ())

  private def doBulkIngest(project: ProjectShortcode) =
    for {
      _           <- ZIO.logInfo(s"Starting bulk ingest for project $project.")
      importDir   <- getImportFolder(project)
      _           <- ZIO.fail(new IOException(s"Import directory '$importDir' does not exist")).unlessZIO(Files.exists(importDir))
      mappingFile <- createMappingFile(project, importDir)
      _           <- ZIO.logInfo(s"Import dir: $importDir, mapping file: $mappingFile")
      total       <- StorageService.findInPath(importDir, FileFilters.isNonHiddenRegularFile).runCount
      _           <- ZIO.logInfo(s"Found $total ingest candidates.")
      sum <-
        StorageService
          .findInPath(importDir, FileFilters.isSupported)
          .mapZIOPar(config.bulkMaxParallel)(file => ingestFileAndUpdateMapping(project, importDir, mappingFile, file))
          .runFold(IngestResult())(_ + _)
      _ <- {
        val countAssets  = sum.success + sum.failed
        val countSuccess = sum.success
        val countFailed  = sum.failed
        val countSkipped = total - countAssets
        ZIO.logInfo(
          s"Finished bulk ingest for project $project. " +
            s"Found $countAssets assets from $total files. " +
            s"Ingested $countSuccess assets successfully, failed $countFailed and skipped $countSkipped files (See logs above for more details).",
        )
      }
    } yield sum

  private def getImportFolder(shortcode: ProjectShortcode): UIO[Path] =
    storage.getTempFolder().map(_ / "import" / shortcode.toString)

  private def createMappingFile(project: ProjectShortcode, importDir: Path): IO[IOException, Path] = {
    val mappingFile = getMappingCsvFile(importDir, project)
    ZIO
      .unlessZIO(Files.exists(mappingFile))(
        Files.createFile(mappingFile) *> Files.writeLines(mappingFile, List("original,derivative")),
      )
      .as(mappingFile)
  }

  private def getMappingCsvFile(importDir: _root_.zio.nio.file.Path, project: ProjectShortcode) =
    importDir.parent.head / s"mapping-$project.csv"

  private def ingestFileAndUpdateMapping(project: ProjectShortcode, importDir: Path, mappingFile: Path, file: Path) =
    ingestService
      .ingestFile(file, project)
      .flatMap(asset => updateMappingCsv(asset, file, importDir, mappingFile))
      .as(IngestResult.success)
      .catchNonFatalOrDie(e =>
        ZIO.logErrorCause(s"Error ingesting file $file: ${e.getMessage}", Cause.fail(e)).as(IngestResult.failed),
      )

  private def updateMappingCsv(asset: Asset, fileToIngest: Path, importDir: Path, csv: Path) =
    ZIO.logInfo(s"Updating mapping file $csv, $asset") *> {
      val ingestedFileRelativePath = CsvUtil.escapeCsvValue(s"${importDir.relativize(fileToIngest)}")
      val derivativeFilename       = CsvUtil.escapeCsvValue(asset.derivative.filename.toString)
      val line                     = s"$ingestedFileRelativePath,$derivativeFilename"
      Files.writeLines(csv, Seq(line), openOptions = Set(StandardOpenOption.APPEND))
    }

  def finalizeBulkIngest(shortcode: ProjectShortcode): Task[Unit] = for {
    _         <- ZIO.logInfo(s"Finalizing bulk ingest for project $shortcode")
    importDir <- getImportFolder(shortcode)
    mappingCsv = getMappingCsvFile(importDir, shortcode)
    _         <- storage.deleteRecursive(importDir)
    _         <- storage.delete(mappingCsv)
    _         <- ZIO.logInfo(s"Finished finalizing bulk ingest for project $shortcode")
  } yield ()

  def getBulkIngestMappingCsv(shortcode: ProjectShortcode): Task[Option[String]] =
    for {
      importDir <- getImportFolder(shortcode)
      mappingCsv = getMappingCsvFile(importDir, shortcode)
      mapping <- ZIO.ifZIO(Files.exists(mappingCsv))(
                   Files.readAllLines(mappingCsv).map(it => Some(it.mkString("\n"))),
                   ZIO.none,
                 )
    } yield mapping
}

object BulkIngestService {
  object Errors {
    def ProjectAlreadyRunning(shortcode: ProjectShortcode) = new IllegalStateException(
      s"Bulk-Ingest for project ${shortcode.value} is already in progress.",
    )
  }
  val layer = ZLayer.fromZIO(TMap.empty[ProjectShortcode, TSemaphore].commit) >>> ZLayer.derive[BulkIngestService]
}

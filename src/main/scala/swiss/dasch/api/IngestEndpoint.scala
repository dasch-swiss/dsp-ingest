/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import org.apache.commons.io.FilenameUtils
import swiss.dasch.api.ApiPathCodecSegments.{ projects, shortcodePathVar }
import swiss.dasch.api.ListProjectsEndpoint.ProjectResponse
import swiss.dasch.domain.SipiImageFormat.Jpx
import swiss.dasch.domain.*
import zio.*
import zio.http.Status
import zio.http.codec.HttpCodec
import zio.http.endpoint.Endpoint
import zio.nio.file.{ Files, Path }

import java.nio.file.StandardOpenOption

object IngestEndpoint {

  private val endpoint = Endpoint
    .post(projects / shortcodePathVar / "bulk-ingest")
    .out[ProjectResponse]
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  private val route = endpoint.implement(shortcode =>
    ApiStringConverters.fromPathVarToProjectShortcode(shortcode).flatMap { code =>
      BulkIngestService.startBulkIngest(code).forkDaemon *>
        ZIO.succeed(ProjectResponse(code))
    }
  )

  val app = route.toApp
}

trait BulkIngestService {

  def startBulkIngest(shortcode: ProjectShortcode): Task[Int]
}

object BulkIngestService {
  def startBulkIngest(shortcode: ProjectShortcode): ZIO[BulkIngestService, Throwable, Int] =
    ZIO.serviceWithZIO[BulkIngestService](_.startBulkIngest(shortcode))
}

final case class BulkIngestServiceLive(
    storage: StorageService,
    sipiClient: SipiClient,
    assetInfo: AssetInfoService,
  ) extends BulkIngestService {

  override def startBulkIngest(shortcode: ProjectShortcode): Task[Int] =
    for {
      _          <- ZIO.logInfo(s"Starting bulk ingest for project $shortcode")
      importDir  <- storage.getTempDirectory().map(_ / "import" / shortcode.value)
      mappingFile = importDir / "mapping.csv"
      _          <- Files.createFile(mappingFile)
      _          <- Files.writeLines(mappingFile, List("original,derivative"))
      sum        <- StorageService
                      .findInPath(importDir, FileFilters.isImage)
                      .mapZIOPar(8)(ingestSingleImage(_, shortcode, mappingFile))
                      .runSum
      _          <- ZIO.logInfo(s"Finished bulk ingest for project $shortcode, ingested $sum images")
    } yield sum

  private def ingestSingleImage(
      file: Path,
      shortcode: ProjectShortcode,
      mappingFile: Path,
    ): Task[Int] =
    for {
      _               <- ZIO.logInfo(s"Ingesting image $file")
      asset           <- Asset.makeNew(shortcode)
      assetDir        <- ensureAssetDirExists(asset)
      originalFile    <- copyFileToAssetDir(file, assetDir, asset)
      derivativeFile  <- transcode(assetDir, originalFile, asset)
      originalFilename = file.filename.toString
      _               <- assetInfo.createAssetInfo(asset, originalFilename)
      _               <- updateMappingCvs(mappingFile, derivativeFile, originalFilename, asset)
      _               <- Files.delete(file)
      _               <- ZIO.logInfo(s"Finished ingesting image $file")
    } yield 1

  private def updateMappingCvs(
      mappingFile: Path,
      derivativeFile: Path,
      originalFilename: String,
      asset: Asset,
    ) =
    ZIO.logInfo(s"Updating mapping file $mappingFile, $asset") *>
      Files.writeLines(
        mappingFile,
        List(s"$originalFilename,${derivativeFile.filename}"),
        openOptions = Set(StandardOpenOption.APPEND),
      )

  private def ensureAssetDirExists(asset: Asset) =
    for {
      _        <- ZIO.logInfo(s"Ensuring asset dir exists, $asset")
      assetDir <- storage.getAssetDirectory(asset)
      _        <- Files.createDirectories(assetDir)
    } yield assetDir

  private def copyFileToAssetDir(
      file: Path,
      assetDir: Path,
      asset: Asset,
    ) = {
    val originalFile = assetDir / s"${asset.id}${FilenameUtils.getExtension(file.filename.toString)}.orig"
    ZIO.logInfo(s"Copying file $file to $assetDir, $asset") *>
      Files.copy(file, originalFile).as(originalFile)
  }

  private def transcode(
      assetDir: Path,
      originalFile: Path,
      asset: Asset,
    ) = {
    val derivativeFile = assetDir / s"${asset.id}.${Jpx.extension}"
    ZIO.logInfo(s"Transcoding $originalFile to $derivativeFile, $asset") *>
      sipiClient
        .transcodeImageFile(originalFile, derivativeFile, Jpx)
        .as(derivativeFile)
  }

}

object BulkIngestServiceLive {
  val layer = ZLayer.fromFunction(BulkIngestServiceLive.apply _)
}

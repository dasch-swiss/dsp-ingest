package swiss.dasch.api

import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import swiss.dasch.Endpoints
import swiss.dasch.config.Configuration
import swiss.dasch.domain.*
import swiss.dasch.infrastructure.*
import zio.{ZIO, ZIOAppDefault}

object DocsGenerator extends ZIOAppDefault {

  override def run: ZIO[Environment, Nothing, Int] = {
    val interpreter = OpenAPIDocsInterpreter()
    for {
      api  <- ZIO.serviceWith[Endpoints](_.api)
      docs <- interpreter.toOpenAPI(api, "", "")
    } yield 0
  }.provide(
    AssetInfoServiceLive.layer,
    AuthServiceLive.layer,
    BaseEndpoints.layer,
    BulkIngestServiceLive.layer,
    CommandExecutorLive.layer,
    Configuration.layer,
    Endpoints.layer,
    FileChecksumServiceLive.layer,
    FileSystemCheckLive.layer,
    HealthCheckServiceLive.layer,
    ImportServiceLive.layer,
    IngestApiServer.layer,
    IngestService.layer,
    MaintenanceActionsLive.layer,
    MaintenanceEndpoints.layer,
    MaintenanceEndpointsHandler.layer,
    Metrics.layer,
    MonitoringEndpoints.layer,
    MonitoringEndpointsHandler.layer,
    MovingImageService.layer,
    ProjectServiceLive.layer,
    ProjectsEndpoints.layer,
    ProjectsEndpointsHandler.layer,
    ReportServiceLive.layer,
    SipiClientLive.layer,
    StillImageServiceLive.layer,
    StorageServiceLive.layer
    //        ZLayer.Debug.mermaid ,
  )
}

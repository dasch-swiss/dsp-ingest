package swiss.dasch

import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import swiss.dasch.api.*
import swiss.dasch.config.Configuration
import swiss.dasch.version.BuildInfo
import zio.{ZIO, ZIOAppDefault}

object DocsGenerator extends ZIOAppDefault {

  override def run = {
    val interp = OpenAPIDocsInterpreter()
    for {
      _   <- ZIO.logInfo("Generating OpenAPI docs")
      mon <- ZIO.serviceWith[MonitoringEndpoints](_.endpoints)
      prj <- ZIO.serviceWith[ProjectsEndpoints](_.endpoints)
      mtn <- ZIO.serviceWith[MaintenanceEndpoints](_.endpoints)
      apis = mon ++ (prj ++ mtn).map(_.endpoint)
      docs = interp.toOpenAPI(apis, BuildInfo.name, BuildInfo.version)
      _   <- ZIO.logInfo(s"Found ${docs.toYaml}")
    } yield 0
  }.provide(
    AuthServiceLive.layer,
    BaseEndpoints.layer,
    Configuration.layer,
    MaintenanceEndpoints.layer,
    MonitoringEndpoints.layer,
    ProjectsEndpoints.layer
    //        ZLayer.Debug.mermaid ,
  )
}

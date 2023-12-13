package swiss.dasch

import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import swiss.dasch.api.*
import swiss.dasch.config.Configuration
import zio.{ZIO, ZIOAppDefault}

object DocsGenerator extends ZIOAppDefault {

  override def run = {
    val interp = OpenAPIDocsInterpreter()
    for {
      _                      <- ZIO.logInfo("Generating OpenAPI docs")
      mon: List[AnyEndpoint] <- ZIO.serviceWith[MonitoringEndpoints](_.endpoints)
      prj                    <- ZIO.serviceWith[ProjectsEndpoints](_.endpoints)
      mtn                    <- ZIO.serviceWith[MaintenanceEndpoints](_.endpoints)
      //      apis: Seq[AnyEndpoint]  = mon ++ prj ++ mtn
      docs                   =interp.toOpenAPI(mon, "SwissDasch", "1.0.0")
      _                      <- ZIO.logInfo(s"Found ${docs.toYaml}")
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

package swiss.dasch

import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import swiss.dasch.api.*
import swiss.dasch.config.Configuration
import swiss.dasch.version.BuildInfo
import zio.nio.file.{Files, Path}
import zio.{Chunk, ZIO, ZIOAppArgs, ZIOAppDefault}

object DocsGenerator extends ZIOAppDefault {
  private val interp: OpenAPIDocsInterpreter = OpenAPIDocsInterpreter()

  override def run = {
    for {
      _    <- ZIO.logInfo("Generating OpenAPI docs")
      args <- getArgs
      mon  <- ZIO.serviceWith[MonitoringEndpoints](_.endpoints)
      prj  <- ZIO.serviceWith[ProjectsEndpoints](_.endpoints.map(_.endpoint))
      mtn  <- ZIO.serviceWith[MaintenanceEndpoints](_.endpoints.map(_.endpoint))
      path  = Path(args.head)
      _    <- safeToFile(mon, path, "monitoring")
      _    <- safeToFile(prj, path, "projects")
      _    <- safeToFile(mtn, path, "maintenance")
      _    <- ZIO.logInfo(s"Found $args")
    } yield 0
  }.provideSome[ZIOAppArgs](
    AuthServiceLive.layer,
    BaseEndpoints.layer,
    Configuration.layer,
    MaintenanceEndpoints.layer,
    MonitoringEndpoints.layer,
    ProjectsEndpoints.layer
    //        ZLayer.Debug.mermaid ,
  )

  private def safeToFile(endpoints: Seq[AnyEndpoint], path: Path, name: String) = {
    val content = interp.toOpenAPI(endpoints, s"${BuildInfo.name}-$name", BuildInfo.version)
    for {
      _     <- ZIO.logInfo(s"Writing to $path")
      target = path / s"openapi-$name.yml"
      _     <- Files.createFile(target)
      _     <- Files.writeBytes(target, Chunk.fromArray(content.toYaml.getBytes))
    } yield ()
  }
}

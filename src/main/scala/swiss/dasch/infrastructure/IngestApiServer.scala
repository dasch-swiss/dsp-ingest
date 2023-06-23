/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.api.monitoring.{ HealthEndpoint, InfoEndpoint }
import swiss.dasch.api.{ Authenticator, ExportEndpoint, ImportEndpoint }
import swiss.dasch.config.Configuration.DspIngestApiConfig
import zio.{ BuildInfo, URLayer, ZIO, ZLayer }
import zio.http.{ App, Server }

object IngestApiServer {

  private val serviceApps    = (ExportEndpoint.app ++ ImportEndpoint.app) @@ Authenticator.middleware
  private val managementApps = HealthEndpoint.app ++ InfoEndpoint.app
  private val app            = managementApps ++ serviceApps

  def startup() =
    ZIO.logInfo(s"Starting ${BuildInfo.name}") *>
      Server.install(app) *>
      ZIO.serviceWithZIO[DspIngestApiConfig](c =>
        ZIO.logInfo(s"Started ${BuildInfo.name} on http://${c.host}:${c.port}/info")
      )
      *>
      ZIO.never

  val layer: URLayer[DspIngestApiConfig, Server] = ZLayer
    .service[DspIngestApiConfig]
    .flatMap { cfg =>
      Server.defaultWith(_.binding(cfg.get.host, cfg.get.port))
    }
    .orDie
}

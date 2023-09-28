/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.api.*
import swiss.dasch.config.Configuration.ServiceConfig
import swiss.dasch.version.BuildInfo
import zio.http.*
import zio.http.internal.middlewares.Cors.CorsConfig
import zio.{ URLayer, ZIO, ZLayer }

object IngestApiServer {

  private val serviceApps = (ImportEndpoint.app) @@ AuthService.middleware

  private val app = serviceApps
    @@ HttpRoutesMiddlewares.dropTrailingSlash
    @@ HttpRoutesMiddlewares.cors(CorsConfig())

  def startup() =
    ZIO.logInfo(s"Starting ${BuildInfo.name}") *>
      Server.install(app) *>
      ZIO.serviceWithZIO[ServiceConfig](c =>
        ZIO.logInfo(s"Started ${BuildInfo.name}/${BuildInfo.version}, see http://${c.host}:${c.port}/docs")
      )

  val layer: URLayer[ServiceConfig, Server] = ZLayer
    .service[ServiceConfig]
    .flatMap { cfg =>
      Server.defaultWith(_.binding(cfg.get.host, cfg.get.port).enableRequestStreaming)
    }
    .orDie
}

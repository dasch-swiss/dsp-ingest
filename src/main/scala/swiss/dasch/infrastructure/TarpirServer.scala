/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.ziohttp
import sttp.tapir.server.ziohttp.{ ZioHttpInterpreter, ZioHttpServerOptions }
import swiss.dasch.Endpoints
import zio.*
import zio.http.*
object TarpirServer {

  private val serverOptions = ZioHttpServerOptions
    .customiseInterceptors
    .metricsInterceptor(ZioMetrics.default[Task]().metricsInterceptor())
    .options

  def startup(): ZIO[Server with Endpoints, Nothing, Unit] = for {
    endpoints <- ZIO.service[Endpoints]
    httpApp    = ZioHttpInterpreter(serverOptions).toHttp(endpoints.endpoints)
    _         <- Server.install(httpApp.withDefaultErrorResponse)
  } yield ()
}

/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import zio.{Chunk, UIO, URIO, ZIO, ZLayer}

trait HealthCheckService {
  def check: UIO[Health]
}
object HealthCheckService {
  def check: URIO[HealthCheckService, Health] = ZIO.serviceWithZIO(_.check)
}

trait HealthIndicator {
  def health: UIO[Health]
}

final case class HealthCheckServiceLive(indicators: Chunk[HealthIndicator]) extends HealthCheckService {
  override def check: UIO[Health] = ZIO.foreach(indicators)(_.health).map(_.reduce(_ aggregate _))
}

object HealthCheckServiceLive {
  val layer =
    ZLayer.fromZIO(for {
      fs <- ZIO.service[FileSystemHealthIndicator]
      db <- ZIO.service[DbHealthIndicator]
    } yield Chunk[HealthIndicator](fs, db)) >>> ZLayer.derive[HealthCheckServiceLive]
}

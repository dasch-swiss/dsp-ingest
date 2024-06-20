/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.infrastructure.Health.Status
import swiss.dasch.infrastructure.Health.Status.UP
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.{Chunk, UIO, URIO, ZIO, ZLayer}

trait HealthCheckService {
  def check: UIO[AggregatedHealth]
}
object HealthCheckService {
  def check: URIO[HealthCheckService, AggregatedHealth] = ZIO.serviceWithZIO(_.check)
}

type HealthIndicatorName = String
trait HealthIndicator {
  def health: UIO[(HealthIndicatorName, Health)]
}

final case class AggregatedHealth(status: Status, components: Option[Map[HealthIndicatorName, Health]]) {
  def isHealthy: Boolean = status == Status.UP
}
object AggregatedHealth {
  given codec: JsonCodec[AggregatedHealth] = DeriveJsonCodec.gen[AggregatedHealth]
  def from(all: Chunk[(HealthIndicatorName, Health)]): AggregatedHealth = {
    val status = all.map(_._2).reduce(_ aggregate _).status
    if (status == UP) {
      AggregatedHealth(status, None)
    } else {
      AggregatedHealth(status, Some(all.toMap))
    }
  }
}

final case class HealthCheckServiceLive(indicators: Chunk[HealthIndicator]) extends HealthCheckService {
  override def check: UIO[AggregatedHealth] = ZIO.foreach(indicators)(_.health).map(AggregatedHealth.from)
}

object HealthCheckServiceLive {
  val layer =
    ZLayer.fromZIO(for {
      fs <- ZIO.service[FileSystemHealthIndicator]
      db <- ZIO.service[DbHealthIndicator]
    } yield Chunk[HealthIndicator](fs, db)) >>> ZLayer.derive[HealthCheckServiceLive]
}

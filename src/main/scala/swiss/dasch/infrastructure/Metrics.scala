package swiss.dasch.infrastructure

import zio.ZLayer
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.{ MetricsConfig, prometheus }
import zio.metrics.jvm.DefaultJvmMetrics
import zio.durationInt

object Metrics {
  val layer: ZLayer[Any, Nothing, PrometheusPublisher] =
    ZLayer.make[PrometheusPublisher](
      ZLayer.succeed(MetricsConfig(interval = 5.seconds)),
      prometheus.publisherLayer,
      prometheus.prometheusLayer,
      DefaultJvmMetrics.live.unit.orDie,
    )
}

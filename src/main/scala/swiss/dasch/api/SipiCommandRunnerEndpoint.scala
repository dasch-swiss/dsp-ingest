package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.sipi
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.SipiConfig
import swiss.dasch.domain.{SipiCommand, SipiCommandRunnerService, SipiCommandRunnerServiceLive}
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}
import zio.http.Status
import zio.http.endpoint.Endpoint
import zio.http.*
import zio.json.{DeriveJsonEncoder, JsonEncoder}
import zio.schema.{DeriveSchema, Schema}
import zio.*

object SipiCommandRunnerEndpoint {
  final case class SipiResponse(response: String)
//  final case class CompareResponse(file1: String, file2: String)
  object SipiResponse {
    def make(response: String): SipiResponse = SipiResponse(response)

    implicit val schema: Schema[SipiResponse]           = DeriveSchema.gen[SipiResponse]
    implicit val jsonEncoder: JsonEncoder[SipiResponse] = DeriveJsonEncoder.gen[SipiResponse]
  }

  private val getHelpResponse = Endpoint
    .get(sipi / "help")
    .out[SipiResponse]
    .outError[InternalProblem](Status.InternalServerError)

  val app: App[SipiCommandRunnerService] =
    getHelpResponse
      .implement(_ =>
        SipiCommandRunnerService
          .help()
          .mapBoth(
            ApiProblem.internalError,
            res => SipiResponse.make(res),
          )
      )
      .toApp
}

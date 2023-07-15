package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.{ commandPathVar, help, sipi }
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.{ SipiConfig, StorageConfig }
import swiss.dasch.domain.*
import zio.http.*
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint.{ Endpoint, Routes }
import zio.json.{ DeriveJsonEncoder, JsonEncoder }
import zio.schema.{ DeriveSchema, Schema }
import zio.*

/** Endpoint that enables execute commands on SIPI container
  */
object SipiTestEndpoint {
  final case class SipiResponse(stdOut: String, stdErr: String)
  object SipiResponse {
    def make(sipiOut: SipiOutput): SipiResponse         = SipiResponse(sipiOut.stdOut, sipiOut.stdErr)
    implicit val schema: Schema[SipiResponse]           = DeriveSchema.gen[SipiResponse]
    implicit val jsonEncoder: JsonEncoder[SipiResponse] = DeriveJsonEncoder.gen[SipiResponse]
  }

  private val helpEndpoint = Endpoint
    .get(sipi / help)
    .out[SipiResponse]
    .outError[InternalProblem](Status.InternalServerError)

  private val compareEndpoint = Endpoint
    .get(sipi / "compare")
    .out[SipiResponse]
    .outError[InternalProblem](Status.InternalServerError)

  private val helpRoute: Routes[SipiClient, InternalProblem, None]                       =
    helpEndpoint.implement(_ =>
      SipiClient.help().mapBoth(ApiProblem.internalError, res => SipiResponse.make(res)).logError
    )
  private val compareRoute: Routes[SipiClient with StorageConfig, InternalProblem, None] =
    compareEndpoint.implement(_ =>
      for {
        assetPath <- ZIO.service[StorageConfig].map(_.assetPath)
        file1      = assetPath / "0001" / "fg" / "il" / "FGiLaT4zzuV-CqwbEDFAFeS.jp2"
        file2      = assetPath / "0001" / "fg" / "il" / "FGiLaT4zzuV-CqwbEDFAFeS.jp2.orig"
        response  <-
          SipiClient.compare(file1, file2).mapBoth(ApiProblem.internalError, out => SipiResponse.make(out)).logError
      } yield response
    )

  val app = (helpRoute ++ compareRoute).toApp
}

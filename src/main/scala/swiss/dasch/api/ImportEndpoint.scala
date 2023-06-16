package swiss.dasch.api
import eu.timepit.refined.auto.autoUnwrap
import swiss.dasch.api.ApiStringConverters.fromPathVarToProjectShortcode
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.ProjectShortcode
import zio.http.Header.{ ContentDisposition, ContentType }
import zio.http.HttpError.*
import zio.http.Path.Segment.Root
import zio.http.codec.*
import zio.http.codec.HttpCodec.*
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint.{ Endpoint, Routes }
import zio.http.{ Header, * }
import zio.json.{ DeriveJsonEncoder, JsonEncoder }
import zio.nio.file
import zio.nio.file.Files
import zio.schema.codec.JsonCodec.JsonEncoder
import zio.schema.{ DeriveSchema, Schema }
import zio.stream.{ ZSink, ZStream }
import zio.{ Chunk, Exit, Scope, URIO, ZIO, ZNothing }

object ImportEndpoint {
  case class UploadResponse(status: String = "okey")
  private object UploadResponse {
    implicit val schema: Schema[UploadResponse]       = DeriveSchema.gen[UploadResponse]
    implicit val encoder: JsonEncoder[UploadResponse] = DeriveJsonEncoder.gen[UploadResponse]
  }

  private val importEndpoint: Endpoint[(String, ZStream[Any, Nothing, Byte]), ApiProblem, UploadResponse, None] =
    Endpoint
      .post("project" / string("shortcode") / "import")
      .inStream[Byte]("upload")
      .out[UploadResponse]
      .outErrors(
        HttpCodec.error[IllegalArguments](Status.BadRequest),
        HttpCodec.error[InternalProblem](Status.InternalServerError),
      )

  val app: App[StorageConfig] = importEndpoint
    .implement((shortcode: String, stream: ZStream[Any, Nothing, Byte]) =>
      for {
        config     <- ZIO.service[StorageConfig] <* ZIO.logInfo("hello")
        pShortcode <- ApiStringConverters.fromPathVarToProjectShortcode(shortcode)
        tempFile    = (config.importPath / s"import-$pShortcode").toFile
        _          <- stream
                        .run(ZSink.fromFile(tempFile))
                        .mapError(ApiProblem.internalError)
      } yield UploadResponse()
    )
    .toApp
}

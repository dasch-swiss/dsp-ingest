package swiss.dasch.api
import eu.timepit.refined.auto.autoUnwrap
import swiss.dasch.api.ApiStringConverters.fromPathVarToProjectShortcode
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.{ AssetService, ProjectShortcode }
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

import java.io.IOException
import java.util.zip.ZipFile

def validateInputFile(tempFile: file.Path): ZIO[Any, ApiProblem, Unit] =
  (for {
    _ <- ZIO
           .fail(IllegalArguments(Map("body" -> "body is empty")))
           .whenZIO(Files.size(tempFile).mapBoth(e => ApiProblem.internalError(e), _ == 0))
    _ <-
      ZIO.scoped {
        val acquire                   = ZIO.attemptBlockingIO(new ZipFile(tempFile.toFile))
        def release(zipFile: ZipFile) = ZIO.succeed(zipFile.close())
        ZIO.acquireRelease(acquire)(release).orElseFail(IllegalArguments(Map("body" -> "body is not a zip file")))
      }
  } yield ()).tapError(e => Files.deleteIfExists(tempFile).mapError(ApiProblem.internalError))

object ImportEndpoint {
  case class UploadResponse(status: String = "okey")
  private object UploadResponse {
    implicit val schema: Schema[UploadResponse]       = DeriveSchema.gen[UploadResponse]
    implicit val encoder: JsonEncoder[UploadResponse] = DeriveJsonEncoder.gen[UploadResponse]
  }

  private val uploadCodec = HeaderCodec.contentType ++ ContentCodec.contentStream[Byte] ++ StatusCodec.status(Status.Ok)

  private val importEndpoint
      : Endpoint[(String, ZStream[Any, Nothing, Byte], ContentType), ApiProblem, UploadResponse, None] =
    Endpoint
      .post("project" / string("shortcode") / "import")
      // Files must be uploaded as zip files with the header 'Content-Type' 'application/zip' and the file in the body.
      // For now we check the ContentType in the implementation as zio-http doesn't support it yet to specify it
      // in the endpoint definition.
      .inCodec(ContentCodec.contentStream[Byte] ++ HeaderCodec.contentType)
      .out[UploadResponse]
      .outErrors(
        HttpCodec.error[IllegalArguments](Status.BadRequest),
        HttpCodec.error[InternalProblem](Status.InternalServerError),
      )

  val app: App[StorageConfig with AssetService] = importEndpoint
    .implement(
      (
          shortcode: String,
          stream: ZStream[Any, Nothing, Byte],
          actual: ContentType,
        ) =>
        for {
          pShortcode <- ApiStringConverters.fromPathVarToProjectShortcode(shortcode)
          _          <- ApiContentTypes.verifyContentType(actual, ApiContentTypes.applicationZip)
          tempFile   <- ZIO.serviceWith[StorageConfig](_.importPath / s"import-$pShortcode.zip")
          _          <- stream
                          .run(ZSink.fromFile(tempFile.toFile))
                          .mapError(ApiProblem.internalError)
          _          <- validateInputFile(tempFile)
          _          <- AssetService
                          .importProject(pShortcode, tempFile)
                          .mapError(ApiProblem.internalError)
        } yield UploadResponse()
    )
    .toApp
}
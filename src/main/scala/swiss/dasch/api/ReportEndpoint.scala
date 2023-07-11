package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.*
import swiss.dasch.domain.{ AssetInfo, ChecksumResult, Report, ReportService }
import zio.http.Header.{ ContentDisposition, ContentType }
import zio.http.HttpError.*
import zio.http.Path.Segment.Root
import zio.http.codec.*
import zio.http.codec.HttpCodec.*
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint.{ Endpoint, Routes }
import zio.http.{ Header, * }
import zio.json.{ DeriveJsonEncoder, JsonEncoder, JsonError }
import zio.nio.file
import zio.schema.{ DeriveSchema, Schema }
import zio.stream.ZStream
import zio.{ Chunk, Exit, Scope, URIO, ZIO, ZNothing }

import scala.collection.immutable.Map
object ReportEndpoint {

  final case class FileChecksumResponse(filename: String, checksumMatches: Boolean)
  object FileChecksumResponse   {
    implicit val encoder: JsonEncoder[FileChecksumResponse] = DeriveJsonEncoder.gen[FileChecksumResponse]
    implicit val schema: Schema[FileChecksumResponse]       = DeriveSchema.gen[FileChecksumResponse]
    def make(result: ChecksumResult): FileChecksumResponse  =
      FileChecksumResponse(result.file.filename.toString, result.checksumMatches)
  }
  final case class ChecksumReportResponse(asset: Map[String, Chunk[FileChecksumResponse]])
  object ChecksumReportResponse {
    implicit val encoder: JsonEncoder[ChecksumReportResponse] = DeriveJsonEncoder.gen[ChecksumReportResponse]
    implicit val schema: Schema[ChecksumReportResponse]       = DeriveSchema.gen[ChecksumReportResponse]

    def make(report: Report): ChecksumReportResponse = {
      val cnt = report.map.map {
        case (key: AssetInfo, value: Chunk[ChecksumResult]) =>
          (key.asset.id.toString, value.map(FileChecksumResponse.make))
      }
      ChecksumReportResponse(cnt)
    }
  }

  private val endpoint = Endpoint
    .get(projects / shortcodePathVar / "checksumreport")
    .out[ChecksumReportResponse]
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  val app = endpoint
    .implement((shortcode: String) =>
      ApiStringConverters.fromPathVarToProjectShortcode(shortcode).flatMap {
        ReportService.verificationReport(_).mapBoth(ApiProblem.internalError, ChecksumReportResponse.make)
      }
    )
    .toApp
}

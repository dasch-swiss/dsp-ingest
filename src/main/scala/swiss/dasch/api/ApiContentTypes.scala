package swiss.dasch.api

import zio.{ IO, ZIO }
import zio.http.Header.ContentType

object ApiContentTypes {
  val applicationZip: ContentType = ContentType.parse("application/zip").toOption.get

  def verifyContentType(actual: ContentType, expected: ContentType): IO[IllegalArguments, Unit] =
    ZIO.fail(ApiProblem.invalidHeaderContentType(actual, expected)).when(actual != expected).unit
}

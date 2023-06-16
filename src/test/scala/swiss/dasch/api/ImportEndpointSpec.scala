package swiss.dasch.api

import zio.test.{ ZIOSpecDefault, assertCompletes, assertTrue }
import swiss.dasch.domain.{ AssetService, AssetServiceLive, ProjectShortcode }
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.existingProject
import zio.http.*
object ImportEndpointSpec extends ZIOSpecDefault {

  private def importUrl(shortcode: String | ProjectShortcode): URL = URL(
    Root / "project" / shortcode.toString / "import"
  )

  private val validContentTypeHeaders = Headers(Header.ContentType(MediaType.application.zip))

  val spec = suite("ImportEndpoint")(
    suite("POST on /project/{shortcode}/import should")(
      test("given the shortcode is invalid, return 400")(for {
        response <-
          ImportEndpoint
            .app
            .runZIO(
              Request.post(Body.empty, importUrl("invalid-shortcode")).updateHeaders(_ => validContentTypeHeaders)
            )
      } yield assertTrue(response.status == Status.BadRequest)),
      test("given the Content-Type header is invalid/not-present, return 400")(for {
        response <-
          ImportEndpoint
            .app
            .runZIO(Request.post(Body.empty, importUrl(existingProject)))
      } yield assertTrue(response.status == Status.BadRequest)),
      test("given the Body is empty, return 400")(for {
        response <-
          ImportEndpoint
            .app
            .runZIO(Request.post(Body.empty, importUrl(existingProject)).updateHeaders(_ => validContentTypeHeaders))
      } yield assertTrue(response.status == Status.BadRequest)),
    )
  ).provide(AssetServiceLive.layer, SpecConfigurations.storageConfigLayer)
}

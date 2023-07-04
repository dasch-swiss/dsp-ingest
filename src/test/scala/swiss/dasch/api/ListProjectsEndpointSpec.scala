package swiss.dasch.api

import swiss.dasch.api.ListProjectsEndpoint.{ ProjectResponse, ProjectsResponse }
import swiss.dasch.domain.{ AssetService, AssetServiceLive }
import swiss.dasch.test.SpecConfigurations
import zio.{ Chunk, http }
import zio.http.{ Request, Root, Status, URL }
import zio.test.{ ZIOSpecDefault, assertCompletes, assertTrue }
import zio.json.*

object ListProjectsEndpointSpec extends ZIOSpecDefault {

  val spec = suite("ListProjectsEndpoint")(
    test("should list non-empty project in test folders") {
      for {
        response <- ListProjectsEndpoint.app.runZIO(Request.get(URL(Root / "projects")))
        body     <- response.body.asString
      } yield assertTrue(response.status == Status.Ok, body == ProjectsResponse(Chunk(ProjectResponse("0001"))).toJson)
    }
  ).provide(AssetServiceLive.layer, SpecConfigurations.storageConfigLayer)
}

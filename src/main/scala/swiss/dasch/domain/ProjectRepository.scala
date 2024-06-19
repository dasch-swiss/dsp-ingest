package swiss.dasch.domain

import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import io.getquill.*
import io.getquill.jdbczio.*
import zio.{Clock, IO, ZIO, ZLayer}

import java.sql.SQLException
import java.time.Instant

type ProjectId = PositiveInt
object ProjectId extends RefinedTypeOps[ProjectId, Int]

final case class Project(id: ProjectId, shortcode: ProjectShortcode)

type DbTask[A] = IO[SQLException, A]

final case class ProjectRepository(quill: Quill.Postgres[SnakeCase]) {
  import quill.*

  private final case class ProjectRow(id: Int, shortcode: String, createdAt: Instant)

  private inline def queryProject = quote(querySchema[ProjectRow](entity = "project"))

  private def toProject(row: ProjectRow): Project =
    Project(ProjectId.unsafeFrom(row.id), ProjectShortcode.unsafeFrom(row.shortcode))

  def findByShortcode(shortcode: ProjectShortcode): DbTask[Option[Project]] =
    run(queryProject.filter(prj => prj.shortcode == lift(shortcode.value))).map(_.map(toProject)).map(_.headOption)

  def addProject(shortcode: ProjectShortcode): DbTask[Project] = for {
    now   <- Clock.instant
    row    = ProjectRow(0, shortcode = shortcode.value, createdAt = now)
    newId <- run(queryProject.insertValue(lift(row)).returningGenerated(_.id))
  } yield Project(ProjectId.unsafeFrom(newId), shortcode)

  def deleteProject(id: ProjectId): DbTask[Long] =
    run(queryProject.filter(prj => prj.id == lift(id.value)).delete).debug

  def deleteProjectByShortcode(shortcode: ProjectShortcode): DbTask[Long] =
    findByShortcode(shortcode).flatMap {
      case Some(prj) => deleteProject(prj.id)
      case None      => ZIO.succeed(0)
    }
}

object ProjectRepository {
  val layer = ZLayer.derive[ProjectRepository]
}

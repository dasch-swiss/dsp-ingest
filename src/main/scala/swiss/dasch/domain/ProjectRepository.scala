/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import io.getquill.*
import io.getquill.jdbczio.*
import swiss.dasch.domain.ProjectId.toProjectIdUnsafe
import swiss.dasch.domain.ProjectShortcode.toShortcodeUnsafe
import zio.{Clock, IO, Task, ZIO, ZLayer}

import java.sql.SQLException
import java.time.Instant

type DbTask[A] = IO[SQLException, A]

final case class ProjectRepository(quill: Quill.Postgres[SnakeCase]) {
  import quill.*

  private final case class ProjectRow(id: Int, shortcode: String, createdAt: Instant)

  private inline def queryProject = quote(querySchema[ProjectRow](entity = "project"))

  private def toProject(row: ProjectRow): Project =
    Project(row.id.toProjectIdUnsafe, row.shortcode.toShortcodeUnsafe, row.createdAt)

  def findByShortcode(shortcode: ProjectShortcode): DbTask[Option[Project]] =
    run(queryProject.filter(prj => prj.shortcode == lift(shortcode.value))).map(_.map(toProject)).map(_.headOption)

  def addProject(shortcode: ProjectShortcode): DbTask[Project] = for {
    now   <- Clock.instant
    row    = ProjectRow(0, shortcode = shortcode.value, createdAt = now)
    newId <- run(queryProject.insertValue(lift(row)).returningGenerated(_.id))
  } yield Project(newId.toProjectIdUnsafe, shortcode, now)

  def deleteProjectById(id: ProjectId): DbTask[Unit] =
    run(queryProject.filter(prj => prj.id == lift(id.value)).delete).unit

  def deleteProjectByShortcode(shortcode: ProjectShortcode): Task[Unit] =
    transaction {
      run(queryProject.filter(_.shortcode == lift(shortcode.value)).map(_.id))
        .map(_.headOption)
        .flatMap {
          case None     => ZIO.unit
          case Some(id) => deleteProjectById(id.toProjectIdUnsafe)
        }
    }
}

object ProjectRepository {
  val layer = ZLayer.derive[ProjectRepository]
}

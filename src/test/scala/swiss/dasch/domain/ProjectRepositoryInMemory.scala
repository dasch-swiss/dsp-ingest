/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.ProjectId.toProjectIdUnsafe
import zio.{Chunk, Clock, Ref, ZLayer}

final case class ProjectRepositoryInMemory(projects: Ref[Chunk[Project]], counter: Ref[Int]) extends ProjectRepository {

  def findById(id: ProjectId): DbTask[Option[Project]] =
    projects.get.map(_.find(_.id == id))

  def findByShortcode(shortcode: ProjectShortcode): DbTask[Option[Project]] =
    projects.get.map(_.find(_.shortcode == shortcode))

  def deleteByShortcode(shortcode: ProjectShortcode): DbTask[Unit] =
    projects.update(_.filterNot(_.shortcode == shortcode))

  def deleteById(id: ProjectId): DbTask[Unit] =
    projects.update(_.filterNot(_.id == id))

  def addProject(shortcode: ProjectShortcode): DbTask[Project] = for {
    now <- Clock.instant
    id  <- counter.getAndUpdate(_ + 1)
    prj  = Project(id.toProjectIdUnsafe, shortcode, now)
    _   <- projects.update(_.appended(prj))
  } yield prj

}

object ProjectRepositoryInMemory {
  val layer = ZLayer.fromZIO(Ref.make(1)) >+> ZLayer.fromZIO(Ref.make[Chunk[Project]](Chunk.empty)) >>> ZLayer
    .derive[ProjectRepositoryInMemory]
}

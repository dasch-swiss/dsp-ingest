/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.ZIO
import zio.test.{ZIOSpecDefault, assertTrue}

object ProjectRepositoryInMemorySpec extends ZIOSpecDefault {

  private val repo      = ZIO.serviceWithZIO[ProjectRepository]
  private val shortcode = ProjectShortcode.unsafeFrom("9999")

  val spec = suite("ProjectRepositorySpec")(
    test("findByShortcode") {
      for {
        prj    <- repo(_.addProject(shortcode))
        actual <- repo(_.findByShortcode(shortcode))
      } yield assertTrue(actual.map(_.shortcode).contains(shortcode))
    },
    test("deleteProjectById") {
      for {
        prj    <- repo(_.addProject(shortcode))
        _      <- repo(_.deleteProjectById(prj.id))
        actual <- repo(_.findByShortcode(shortcode))
      } yield assertTrue(actual.isEmpty)
    },
    test("deleteProjectByShortcode") {
      for {
        prj    <- repo(_.addProject(shortcode))
        _      <- repo(_.deleteProjectByShortcode(shortcode))
        actual <- repo(_.findByShortcode(shortcode))
      } yield assertTrue(actual.isEmpty)
    },
  ).provide(ProjectRepositoryInMemory.layer)
}

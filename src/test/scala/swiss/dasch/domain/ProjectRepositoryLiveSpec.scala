/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.util.TestUtils
import zio.ZIO
import zio.test.{ZIOSpecDefault, assertTrue}

object ProjectRepositoryLiveSpec extends ZIOSpecDefault {

  private val repo      = ZIO.serviceWithZIO[ProjectRepository]
  private val shortcode = ProjectShortcode.unsafeFrom("9999")

  val spec = suite("ProjectRepositoryLive")(
    test("findByShortcode") {
      for {
        prj    <- repo(_.addProject(shortcode))
        actual <- repo(_.findByShortcode(shortcode))
      } yield assertTrue(actual.contains(prj))
    },
    test("findById") {
      for {
        prj    <- repo(_.addProject(shortcode))
        actual <- repo(_.findById(prj.id))
      } yield assertTrue(actual.contains(prj))
    },
    test("deleteProjectById") {
      for {
        prj    <- repo(_.addProject(shortcode))
        _      <- repo(_.deleteById(prj.id))
        actual <- repo(_.findByShortcode(shortcode))
      } yield assertTrue(actual.isEmpty)
    },
    test("deleteProjectByShortcode") {
      for {
        prj    <- repo(_.addProject(shortcode))
        _      <- repo(_.deleteByShortcode(shortcode))
        actual <- repo(_.findByShortcode(shortcode))
      } yield assertTrue(actual.isEmpty)
    },
  ).provide(TestUtils.testDbLayerWithEmptyDb, ProjectRepositoryLive.layer)
}

/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecConstants.*
import swiss.dasch.test.SpecPaths.pathFromResource
import swiss.dasch.test.{ SpecConfigurations, SpecPaths }
import zio.nio.file.{ Files, Path }
import zio.test.{ Spec, TestEnvironment, ZIOSpecDefault, assertCompletes, assertTrue }
import zio.{ Chunk, Scope, ZIO, ZLayer }

object ProjectServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AssetServiceSpec")(
      test("should list all projects which contain assets in the asset directory") {
        for {
          projects <- ProjectService.listAllProjects()
        } yield assertTrue(projects == Chunk(existingProject))
      },
      suite("findProject path")(
        test("should find existing projects which contain at least one non hidden regular file") {
          for {
            project <- ProjectService.findProject(existingProject)
          } yield assertTrue(project.isDefined)
        },
        test("should not find not existing projects") {
          for {
            project <- ProjectService.findProject(nonExistentProject)
          } yield assertTrue(project.isEmpty)
        },
      ),
      suite("zipping a project")(
        test("given it does not exist, should return None") {
          for {
            zip <- ProjectService.zipProject(nonExistentProject)
          } yield assertTrue(zip.isEmpty)
        },
        test("given it does exists, should zip and return file path") {
          for {
            tempDir <- ZIO.serviceWith[StorageConfig](_.tempPath)
            zip     <- ProjectService.zipProject(existingProject)
          } yield assertTrue(zip.contains(tempDir / "zipped" / "0001.zip"))
        },
      ),
    ).provide(ProjectServiceLive.layer, SpecConfigurations.storageConfigLayer, StorageServiceLive.layer)
}
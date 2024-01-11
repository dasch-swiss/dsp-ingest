/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.AugmentedPath.JpxDerivativeFile.given
import swiss.dasch.domain.AugmentedPath.MovingImageDerivativeFile.given
import swiss.dasch.domain.AugmentedPath.ProjectFolder.given
import swiss.dasch.domain.AugmentedPath.*
import swiss.dasch.domain.AugmentedPathSpec.ExpectedErrorMessages.{
  hiddenFile,
  noAssetIdInFilename,
  notAProjectFolder,
  unsupportedFileType
}
import swiss.dasch.test.SpecConstants
import zio.nio.file.Path
import zio.test.{Gen, Spec, ZIOSpecDefault, assertTrue, check}

object AugmentedPathSpec extends ZIOSpecDefault {

  private val someAssetId   = SpecConstants.AssetIds.existingAsset.value
  private val someProjectId = SpecConstants.Projects.existingProject.value

  object ExpectedErrorMessages {
    val hiddenFile: String          = "Hidden file."
    val unsupportedFileType: String = "Unsupported file type."
    val noAssetIdInFilename: String = "No AssetId in filename."
    val notAProjectFolder: String   = "Not a project folder."
  }

  private val projectFolderSuite = suite("ProjectFolder")(
    test("can be created from a Path which is a project folder") {
      val path: Path                            = Path(s"/tmp/$someProjectId/")
      val actual: Either[String, ProjectFolder] = AugmentedPath.from(path)
      assertTrue(
        actual.map(_.path).contains(path),
        actual.map(_.shortcode).contains(someProjectId)
      )
    },
    test("cannot be created from a Path which is hidden") {
      val path: Path                            = Path(s"/tmp/.$someProjectId/")
      val actual: Either[String, ProjectFolder] = AugmentedPath.from(path)
      assertTrue(actual == Left(hiddenFile))
    },
    test("cannot be created from a Path which is not a project folder") {
      val path: Path                            = Path("/tmp/not-a-project-folder/")
      val actual: Either[String, ProjectFolder] = AugmentedPath.from(path)
      assertTrue(actual == Left(notAProjectFolder))
    }
  )

  private val jpxDerivativeFileSuite =
    suite("JpxDerivativeFile")(
      test("can be created from a Path which is a derivative file jpx") {
        val gen = Gen.fromIterable(List("jpx", "JPX", "jp2", "JP2"))
        check(gen) { extension =>
          val path: Path                                = Path(s"/tmp/$someAssetId.$extension")
          val actual: Either[String, JpxDerivativeFile] = AugmentedPath.from(path)
          assertTrue(
            actual.map(_.path).contains(path),
            actual.map(_.assetId).contains(someAssetId)
          )
        }
      },
      test("cannot be created if filename is not a valid AssetId") {
        val path: Path                                = Path("/tmp/this_is_no_asset_id!.jpx")
        val actual: Either[String, JpxDerivativeFile] = AugmentedPath.from(path)
        assertTrue(actual == Left(noAssetIdInFilename))
      },
      test("cannot be created from original file") {
        val path: Path                                = Path(s"/tmp/$someAssetId.orig")
        val actual: Either[String, JpxDerivativeFile] = AugmentedPath.from(path)
        assertTrue(actual == Left(unsupportedFileType))
      },
      test("cannot be created from directory") {
        val path: Path                                = Path(s"/tmp/hello/")
        val actual: Either[String, JpxDerivativeFile] = AugmentedPath.from(path)
        assertTrue(actual == Left(unsupportedFileType))
      },
      test("cannot be created from hidden file") {
        val hiddenFiles = Gen.fromIterable(List(s".$someAssetId.txt", s".$someAssetId.jpx"))
        check(hiddenFiles) { filename =>
          val path: Path                                = Path(s"/tmp/$filename")
          val actual: Either[String, JpxDerivativeFile] = AugmentedPath.from(path)
          assertTrue(actual == Left(hiddenFile))
        }
      }
    )

  private val movingImageDerivativeFileSuite = suite("MovingImageDerivativeFile")(
    test("can be created from a Path which is a derivative file mov") {
      val gen = Gen.fromIterable(List("mp4", "MP4"))
      check(gen) { extension =>
        val path: Path                                        = Path(s"/tmp/$someAssetId.$extension")
        val actual: Either[String, MovingImageDerivativeFile] = AugmentedPath.from(path)
        assertTrue(
          actual.map(_.path).contains(path),
          actual.map(_.assetId).contains(someAssetId)
        )
      }
    },
    test("cannot be created if filename is not a valid AssetId") {
      val path: Path                                        = Path("/tmp/this_is_no_asset_id!.mp4")
      val actual: Either[String, MovingImageDerivativeFile] = AugmentedPath.from(path)
      assertTrue(actual == Left(noAssetIdInFilename))
    },
    test("cannot be created from original file") {
      val path: Path                                        = Path(s"/tmp/$someAssetId.orig")
      val actual: Either[String, MovingImageDerivativeFile] = AugmentedPath.from(path)
      assertTrue(actual == Left(unsupportedFileType))
    },
    test("cannot be created from directory") {
      val path: Path                                        = Path(s"/tmp/hello/")
      val actual: Either[String, MovingImageDerivativeFile] = AugmentedPath.from(path)
      assertTrue(actual == Left(unsupportedFileType))
    },
    test("cannot be created from hidden file") {
      val hiddenFiles = Gen.fromIterable(List(s".$someAssetId.mp4", s".$someAssetId.MP4"))
      check(hiddenFiles) { filename =>
        val path: Path                                        = Path(s"/tmp/$filename")
        val actual: Either[String, MovingImageDerivativeFile] = AugmentedPath.from(path)
        assertTrue(actual == Left(hiddenFile))
      }
    }
  )

  private val origFileSuite = suite("OrigFile")(
    test("can be created if Path ends with .orig") {
      val path: Path                       = Path(s"/tmp/$someAssetId.orig")
      val actual: Either[String, OrigFile] = AugmentedPath.from(path)
      assertTrue(
        actual.map(_.path).contains(path),
        actual.map(_.assetId).contains(someAssetId)
      )
    },
    test("cannot be created if filename is not a valid AssetId") {
      val path: Path                       = Path("/tmp/this_is_no_asset_id!.orig")
      val actual: Either[String, OrigFile] = AugmentedPath.from(path)
      assertTrue(actual == Left(noAssetIdInFilename))
    },
    test("cannot be created if file is not an .orig file, e.g. directory or other extension") {
      val invalidFileExtensions = Gen.fromIterable(List(".png", ".orig.tiff", "/", ".txt", ""))
      check(invalidFileExtensions) { extension =>
        val path: Path                       = Path(s"/tmp/$someAssetId$extension")
        val actual: Either[String, OrigFile] = AugmentedPath.from(path)
        assertTrue(actual == Left(unsupportedFileType))
      }
    },
    test("cannot be created from hidden file") {
      val hiddenFiles = Gen.fromIterable(List(s".$someAssetId.txt", s".$someAssetId.orig", s".$someAssetId.tiff.orig"))
      check(hiddenFiles) { filename =>
        val path: Path                       = Path(s"/tmp/$filename")
        val actual: Either[String, OrigFile] = AugmentedPath.from(path)
        assertTrue(actual == Left(hiddenFile))
      }
    }
  )

  private val otherDerivativeFileSuite = suite("OtherDerivativeFile")(
    test("can be created from a Path which is a derivative file") {
      val gen = Gen.fromIterable(SupportedFileType.OtherFiles.extensions)
      check(gen) { extension =>
        val path: Path                                  = Path(s"/tmp/$someAssetId.$extension")
        val actual: Either[String, OtherDerivativeFile] = AugmentedPath.from(path)
        assertTrue(
          actual.map(_.path).contains(path),
          actual.map(_.assetId).contains(someAssetId)
        )
      }
    },
    test("cannot be created if filename is not a valid AssetId") {
      val path: Path                                  = Path("/tmp/this_is_no_asset_id!.txt")
      val actual: Either[String, OtherDerivativeFile] = AugmentedPath.from(path)
      assertTrue(actual == Left(noAssetIdInFilename))
    },
    test("cannot be created from original file") {
      val path: Path                                  = Path(s"/tmp/$someAssetId.orig")
      val actual: Either[String, OtherDerivativeFile] = AugmentedPath.from(path)
      assertTrue(actual == Left(unsupportedFileType))
    },
    test("cannot be created from directory") {
      val path: Path                                  = Path(s"/tmp/hello/")
      val actual: Either[String, OtherDerivativeFile] = AugmentedPath.from(path)
      assertTrue(actual == Left(unsupportedFileType))
    },
    test("cannot be created from hidden file") {
      val hiddenFiles = Gen.fromIterable(List(s".$someAssetId.txt", s".$someAssetId.TXT"))
      check(hiddenFiles) { filename =>
        val path: Path                                  = Path(s"/tmp/$filename")
        val actual: Either[String, OtherDerivativeFile] = AugmentedPath.from(path)
        assertTrue(actual == Left(hiddenFile))
      }
    }
  )

  val spec: Spec[Any, Any] = suite("AugmentedPath")(
    projectFolderSuite,
    jpxDerivativeFileSuite,
    movingImageDerivativeFileSuite,
    origFileSuite,
    otherDerivativeFileSuite
  )
}

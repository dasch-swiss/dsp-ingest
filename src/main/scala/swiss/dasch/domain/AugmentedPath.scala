/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.PathOps.{fileExtension, isHidden}
import swiss.dasch.domain.SupportedFileType.{MovingImage, OtherFiles}
import zio.nio.file.Path

import scala.util.Left

trait AugmentedPathBuilder[A <: AugmentedPath] {
  final def from(str: String): Either[String, A] = from(Path(str))
  def from(str: Path): Either[String, A]
}

trait AugmentedPath {
  def path: Path
}

trait AugmentedFile extends AugmentedPath {
  inline def file: Path        = path
  def filename: NonEmptyString = NonEmptyString.unsafeFrom(file.filename.toString)
}

trait AssetFile extends AugmentedFile {
  def assetId: AssetId
}

trait DerivativeFile extends AugmentedFile

object AugmentedPath {
  import ErrorMessages.*

  private[AugmentedPath] object ErrorMessages {
    val hiddenFile: String          = "Hidden file."
    val unsupportedFileType: String = "Unsupported file type."
    val noAssetIdInFilename: String = "No AssetId in filename."
    val notAProjectFolder: String   = "Not a project folder."
  }

  final case class ProjectFolder(path: Path, shortcode: ProjectShortcode) extends AugmentedPath
  object ProjectFolder {
    given AugmentedPathBuilder[ProjectFolder] with {
      def from(path: Path): Either[String, ProjectFolder] =
        path match {
          case _ if path.isHidden => Left(hiddenFile)
          case _ =>
            ProjectShortcode
              .from(path.elements.last.toString)
              .map(code => ProjectFolder(path, code))
              .left
              .map(_ => notAProjectFolder)
        }
    }
  }

  final case class JpxDerivativeFile private (path: Path, assetId: AssetId) extends DerivativeFile
  object JpxDerivativeFile {

    given AugmentedPathBuilder[JpxDerivativeFile] with {
      def from(path: Path): Either[String, JpxDerivativeFile] =
        path match {
          case _ if path.isHidden => Left(hiddenFile)
          case _ if SipiImageFormat.Jpx.acceptsExtension(path.fileExtension) =>
            AssetId.fromPath(path).map(JpxDerivativeFile(path, _)).toRight(noAssetIdInFilename)
          case _ => Left(unsupportedFileType)
        }
    }
  }

  final case class MovingImageDerivativeFile private (path: Path, assetId: AssetId) extends DerivativeFile
  object MovingImageDerivativeFile {

    given AugmentedPathBuilder[MovingImageDerivativeFile] with {
      def from(path: Path): Either[String, MovingImageDerivativeFile] =
        path match {
          case _ if path.isHidden => Left(hiddenFile)
          case _ if MovingImage.acceptsExtension(path.fileExtension) =>
            AssetId.fromPath(path).map(MovingImageDerivativeFile(path, _)).toRight(noAssetIdInFilename)
          case _ => Left(unsupportedFileType)
        }
    }
  }

  final case class OrigFile private (path: Path, assetId: AssetId) extends AssetFile
  object OrigFile {

    given AugmentedPathBuilder[OrigFile] with {
      def from(path: Path): Either[String, OrigFile] =
        path match {
          case _ if path.isHidden => Left(hiddenFile)
          case _ if path.fileExtension == "orig" =>
            AssetId.fromPath(path).map(OrigFile(path, _)).toRight(noAssetIdInFilename)
          case _ => Left(unsupportedFileType)
        }
    }
  }

  final case class OtherDerivativeFile private (path: Path, assetId: AssetId) extends DerivativeFile
  object OtherDerivativeFile {

    given AugmentedPathBuilder[OtherDerivativeFile] with {
      def from(path: Path): Either[String, OtherDerivativeFile] =
        path match {
          case _ if path.isHidden => Left(hiddenFile)
          case _ if OtherFiles.acceptsExtension(path.fileExtension) =>
            AssetId.fromPath(path).map(OtherDerivativeFile(path, _)).toRight(noAssetIdInFilename)
          case _ => Left(unsupportedFileType)
        }
    }
  }

  def unsafeFrom[A <: AugmentedPath](path: Path)(using b: AugmentedPathBuilder[A]): A =
    from(path).fold(e => throw new IllegalArgumentException(e), identity)
  def unsafeFrom[A <: AugmentedPath](str: String)(using b: AugmentedPathBuilder[A]): A           = unsafeFrom(Path(str))
  def from[A <: AugmentedPath](path: Path)(using b: AugmentedPathBuilder[A]): Either[String, A]  = b.from(path)
  def from[A <: AugmentedPath](str: String)(using b: AugmentedPathBuilder[A]): Either[String, A] = b.from(str)
}

/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.PathOps.fileExtension
import zio.nio.file.Path
import zio.{IO, ZIO, ZLayer}

import java.io.IOException

final case class MimeType private (value: NonEmptyString) extends AnyVal
object MimeType {

  def unsafeFrom(str: String): MimeType =
    from(str).fold(msg => throw new IllegalArgumentException(msg), identity)

  def from(str: String): Either[String, MimeType] =
    Option(str)
      .toRight("Mime type cannot be null")
      .flatMap(it => NonEmptyString.from(it).left.map(_ => "Mime type cannot be empty"))
      .map(MimeType.apply)
}

final case class MimeTypeGuesser() {

  private val allMappings: Map[String, MimeType] = SupportedFileType.values.flatMap(_.mappings).toMap

  def guess(file: Path): IO[IOException, Option[MimeType]] =
    ZIO.succeed(allMappings.get(file.fileExtension))
}

object MimeTypeGuesser {

  def guess(file: Path): ZIO[MimeTypeGuesser, IOException, Option[MimeType]] =
    ZIO.serviceWithZIO[MimeTypeGuesser](_.guess(file))

  val layer = ZLayer.derive[MimeTypeGuesser]
}

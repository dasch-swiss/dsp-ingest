/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.SipiClientMockMethodInvocation.*
import swiss.dasch.domain.Exif.Image.OrientationValue
import swiss.dasch.domain.{ Exif, SipiClient, SipiImageFormat, SipiOutput }
import zio.*
import zio.nio.file.*

import java.nio.file.StandardCopyOption

sealed trait SipiClientMockMethodInvocation
object SipiClientMockMethodInvocation {
  final case class TranscodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ) extends SipiClientMockMethodInvocation
  final case class ApplyTopLeftCorrection(fileIn: Path, fileOut: Path) extends SipiClientMockMethodInvocation
  final case class QueryImageFile(file: Path)                          extends SipiClientMockMethodInvocation
}

final case class SipiClientMock(
    invocations: Ref[List[SipiClientMockMethodInvocation]],
    imageOrientation: Ref[OrientationValue],
  ) extends SipiClient {

  override def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): UIO[SipiOutput] =
    Files.copy(fileIn, fileOut, StandardCopyOption.REPLACE_EXISTING).orDie *>
      invocations
        .update(_.appended(TranscodeImageFile(fileIn, fileOut, outputFormat)))
        .as(SipiOutput("", ""))

  override def applyTopLeftCorrection(fileIn: Path, fileOut: Path): UIO[SipiOutput] =
    Files.copy(fileIn, fileOut, StandardCopyOption.REPLACE_EXISTING).orDie *>
      invocations
        .update(_.appended(ApplyTopLeftCorrection(fileIn, fileOut)))
        .as(SipiOutput("", ""))

  override def queryImageFile(file: Path): UIO[SipiOutput] = for {
    orientation <- imageOrientation.get.map(_.value)
    _           <- invocations.update(_.appended(QueryImageFile(file)))
  } yield SipiOutput(s"Exif.Image.Orientation                       0x0112 Short       1  $orientation", "")

  def getInvocations(): UIO[List[SipiClientMockMethodInvocation]] = invocations.get

  def wasInvoked(invocation: SipiClientMockMethodInvocation): UIO[Boolean] =
    getInvocations().map(_.contains(invocation))

  def noInteractions(): UIO[Boolean] = getInvocations().map(_.isEmpty)

  def setOrientation(orientation: OrientationValue): UIO[Unit] = imageOrientation.set(orientation)
}

object SipiClientMock {
  def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): RIO[SipiClientMock, SipiOutput] =
    ZIO.serviceWithZIO[SipiClientMock](_.transcodeImageFile(fileIn, fileOut, outputFormat))

  def applyTopLeftCorrection(fileIn: Path, fileOut: Path): RIO[SipiClientMock, SipiOutput] =
    ZIO.serviceWithZIO[SipiClientMock](_.applyTopLeftCorrection(fileIn, fileOut))

  def queryImageFile(file: Path): RIO[SipiClientMock, SipiOutput] =
    ZIO.serviceWithZIO[SipiClientMock](_.queryImageFile(file))

  def getInvocations(): RIO[SipiClientMock, List[SipiClientMockMethodInvocation]] =
    ZIO.serviceWithZIO[SipiClientMock](_.getInvocations())

  def wasInvoked(invocation: SipiClientMockMethodInvocation): RIO[SipiClientMock, Boolean] =
    ZIO.serviceWithZIO[SipiClientMock](_.wasInvoked(invocation))

  def noInteractions(): RIO[SipiClientMock, Boolean] =
    ZIO.serviceWithZIO[SipiClientMock](_.noInteractions())

  def setOrientation(orientation: OrientationValue): RIO[SipiClientMock, Unit] =
    ZIO.serviceWithZIO[SipiClientMock](_.setOrientation(orientation))

  val layer: ULayer[SipiClientMock] = ZLayer.fromZIO(for {
    invocations <- Ref.make(List.empty[SipiClientMockMethodInvocation])
    orientation <- Ref.make[OrientationValue](OrientationValue.Horizontal)
  } yield SipiClientMock(invocations, orientation))
}
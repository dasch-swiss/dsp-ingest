package swiss.dasch.domain

import zio.ZIO
import zio.nio.file.{Files, Path}

object AssetInfoFileTestHelper {
  val testProject = ProjectShortcode.unsafeFrom("0001")
  val testChecksumOriginal =
    Sha256Hash.unsafeFrom("fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c")
  val testChecksumDerivative =
    Sha256Hash.unsafeFrom("0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc")

  def createInfoFile(
    originalFileExt: String,
    derivativeFileExt: String,
    customJsonProps: Option[String] = None
  ): ZIO[StorageService, Throwable, (AssetRef, Path)] =
    for {
      assetRef      <- AssetRef.makeNew(testProject)
      assetDir      <- StorageService.getAssetDirectory(assetRef).tap(Files.createDirectories(_))
      simpleInfoFile = assetDir / s"${assetRef.id}.info"
      _             <- Files.createFile(simpleInfoFile)
      _ <- Files.writeLines(
             simpleInfoFile,
             List(s"""{
                     |    ${customJsonProps.map(_ + ",").getOrElse("")}
                     |    "internalFilename" : "${assetRef.id}.$derivativeFileExt",
                     |    "originalInternalFilename" : "${assetRef.id}.$originalFileExt.orig",
                     |    "originalFilename" : "test.$originalFileExt",
                     |    "checksumOriginal" : "$testChecksumOriginal",
                     |    "checksumDerivative" : "$testChecksumDerivative"
                     |}
                     |""".stripMargin)
           )
    } yield (assetRef, assetDir)
}

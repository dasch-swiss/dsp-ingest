/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.config.Configuration.SipiConfig
import swiss.dasch.domain.StorageService
import zio.{Task, ZIO, ZLayer}

import scala.sys.process.{ProcessLogger, stringToProcess}

case class ProcessOutput(stdOut: String, stdErr: String)
final case class Command private[infrastructure] (cmd: String)
final case class CommandExecutor(sipiConfig: SipiConfig, storageService: StorageService) {

  def buildCommand(command: String, params: String): Task[Command] =
    for {
      assetDir <- storageService.getAssetDirectory().flatMap(_.toAbsolutePath)
    } yield
      if (sipiConfig.useLocalDev) {
        Command(s"docker run --entrypoint $command -v $assetDir:$assetDir daschswiss/knora-sipi:latest $params")
      } else {
        Command(s"$command $params")
      }

  private class InMemoryProcessLogger extends ProcessLogger {
    private val sbOut = new StringBuilder
    private val sbErr = new StringBuilder

    override def out(s: => String): Unit = sbOut.append(s + "\n")

    override def err(s: => String): Unit = sbErr.append(s + "\n")

    override def buffer[T](f: => T): T = f

    def getOutput: ProcessOutput = ProcessOutput(sbOut.toString(), sbErr.toString())
  }

  def execute(command: Command): Task[ProcessOutput] = {
    val logger = new InMemoryProcessLogger()
    for {
      _   <- ZIO.logInfo(s"Executing command: ${command.cmd}")
      out <- ZIO.attemptBlockingIO(command.cmd ! logger).as(logger.getOutput)
    } yield out
  }
}

object CommandExecutor {
  val layer = ZLayer.derive[CommandExecutor]
}

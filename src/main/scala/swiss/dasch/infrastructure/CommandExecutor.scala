/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.config.Configuration.SipiConfig
import swiss.dasch.domain.StorageService
import zio.{Task, ZIO, ZLayer}

import scala.sys.process.{ProcessLogger, stringToProcess}

final case class ProcessOutput(stdout: String, stderr: String, exitCode: Int)
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

    def buildOutput(exitCode: Int): ProcessOutput = ProcessOutput(sbOut.toString(), sbErr.toString(), exitCode)
  }

  def execute(command: Command): Task[ProcessOutput] = {
    val logger = new InMemoryProcessLogger()
    for {
      _   <- ZIO.logInfo(s"Executing command: ${command.cmd}")
      out <- ZIO.attemptBlockingIO(command.cmd !< logger).map(logger.buildOutput)
      _   <- ZIO.logWarning(s"Command '${command.cmd}' stderr output: '${out.stdout}''").when(out.stderr.nonEmpty)
    } yield out
  }

  /**
   * Executes a command and returns the standard output.
   * Fails if the command fails wih a non-zero exit code or
   * if the stderr is not empty.
   *
   * @param command the command to execute.
   * @return the standard output.
   */
  def executeOrFail(command: Command): Task[ProcessOutput] =
    execute(command).flatMap { out =>
      if (out.exitCode != 0) { ZIO.fail(new RuntimeException(s"Command failed: '${command.cmd}' : '${out.stderr}'")) }
      else { ZIO.succeed(out) }
    }
}

object CommandExecutor {
  val layer = ZLayer.derive[CommandExecutor]
}

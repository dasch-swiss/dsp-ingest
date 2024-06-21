/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.ProjectLocker.ProjectLocked
import zio.*
import zio.stm.{TMap, TSemaphore}
import zio.test.*

final case class ProjectLocker(private val semaphoresPerProject: TMap[ProjectShortcode, TSemaphore]) {

  private def getLock(key: ProjectShortcode): IO[ProjectLocked, TSemaphore] = (for {
    semaphore <- semaphoresPerProject.getOrElseSTM(key, TSemaphore.make(1))
    _         <- semaphoresPerProject.put(key, semaphore)
    _         <- semaphore.acquire
  } yield semaphore).commit
    .timeout(ProjectLocker.acquireTimeOut)
    .some
    .orElseFail(ProjectLocked(key))

  def withSemaphore[E, A](key: ProjectShortcode)(zio: IO[E, A]): IO[ProjectLocked | E, A] =
    getLock(key).flatMap(semaphore => zio.logError.ensuring(semaphore.release.commit))

  def withSemaphoreForkDaemon[E, A](key: ProjectShortcode)(zio: IO[E, A]): IO[ProjectLocked, Fiber.Runtime[E, A]] =
    getLock(key).flatMap(semaphore => zio.logError.ensuring(semaphore.release.commit).forkDaemon)
}

object ProjectLocker {
  final case class ProjectLocked(shortcode: ProjectShortcode)
  val acquireTimeOut: Duration = 2.seconds
  val layer                    = ZLayer.fromZIO(TMap.empty[ProjectShortcode, TSemaphore].commit) >>> ZLayer.derive[ProjectLocker]
}

object ProjectLockerSpec extends ZIOSpecDefault {

  private val shortcode: ProjectShortcode       = ProjectShortcode.unsafeFrom("0001")
  private val lock                              = ZIO.serviceWithZIO[ProjectLocker]
  private def withSemaphore[E](zio: IO[E, Int]) = lock(_.withSemaphore(shortcode)(zio))
//  private def withSemaphoreForkDaemon(zio: UIO[Int])       = lock(_.withSemaphoreForkDaemon(shortcode)(zio))
  private def slowSuccessTask(nr: Int, duration: Duration) = ZIO.succeed(nr).delay(duration)
  private def slowFailedTask(nr: Int, duration: Duration)  = ZIO.fail(nr).delay(duration)

  val spec = suite("ProjectLocker")(
    suite("withSemaphore")(
      suite("with success task")(
        test("should run a success task") {
          for {
            fork <- lock(_.withSemaphore(shortcode)(slowSuccessTask(1, 1.second))).fork
            _    <- TestClock.adjust(1.second)
            exit <- fork.join
          } yield assertTrue(exit == 1)
        },
        test("should prevent another task to run if lock is not acquired within acquireTimeout") {
          for {
            fork1   <- withSemaphore(slowSuccessTask(1, 3.seconds)).fork
            _       <- TestClock.adjust(1.second) // wait 1 seconds so that task cannot acquire lock before the other is done
            fork2   <- withSemaphore(slowSuccessTask(2, 3.seconds)).fork
            _       <- TestClock.adjust(10.seconds)
            result1 <- fork1.join
            exit2   <- fork2.join.exit
          } yield assertTrue(result1 == 1, exit2 == Exit.fail(ProjectLocked(shortcode)))
        },
        test("should allow another task to run if lock is acquired within acquireTimeout") {
          for {
            fork1   <- withSemaphore(slowSuccessTask(1, 3.seconds)).fork
            _       <- TestClock.adjust(2.seconds) // wait 2 seconds so that task can acquire lock after the other is done
            fork2   <- withSemaphore(slowSuccessTask(2, 3.seconds)).fork
            _       <- TestClock.adjust(10.seconds)
            result1 <- fork1.join
            result2 <- fork2.join
          } yield assertTrue(result1 == 1, result2 == 2)
        },
      ),
      suite("with failed task")(
        test("should run a success task") {
          for {
            fork <- lock(_.withSemaphore(shortcode)(slowFailedTask(1, 1.second))).fork
            _    <- TestClock.adjust(1.second)
            exit <- fork.join.exit
          } yield assertTrue(exit == Exit.fail(1))
        },
        test("should prevent another task to run if lock is not acquired within acquireTimeout") {
          for {
            fork1 <- withSemaphore(slowFailedTask(1, 3.seconds)).fork
            _     <- TestClock.adjust(1.second) // wait 1 seconds so that task cannot acquire lock before the other is done
            fork2 <- withSemaphore(slowFailedTask(2, 3.seconds)).fork
            _     <- TestClock.adjust(10.seconds)
            exit1 <- fork1.join.exit
            exit2 <- fork2.join.exit
          } yield assertTrue(exit1 == Exit.fail(1), exit2 == Exit.fail(ProjectLocked(shortcode)))
        },
        test("should allow another task to run if lock is acquired within acquireTimeout") {
          for {
            fork1 <- withSemaphore(slowFailedTask(1, 3.seconds)).fork
            _     <- TestClock.adjust(2.seconds) // wait 2 seconds so that task can acquire lock after the other is done
            fork2 <- withSemaphore(slowFailedTask(2, 3.seconds)).fork
            _     <- TestClock.adjust(10.seconds)
            exit1 <- fork1.join.exit
            exit2 <- fork2.join.exit
          } yield assertTrue(exit1 == Exit.fail(1), exit2 == Exit.fail(2))
        },
      ),
    ),
  ).provide(ProjectLocker.layer)
}

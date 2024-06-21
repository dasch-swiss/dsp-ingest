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
  } yield semaphore).commit.disconnect
    .timeout(ProjectLocker.acquireTimeOut)
    .some
    .orElseFail(ProjectLocked(key))

  def withSemaphore[E, A](key: ProjectShortcode)(
    zio: IO[E, A],
  ): ZIO[Any, ProjectLocked | E, A] =
    for {
      semaphore <- getLock(key)
      result    <- zio.logError.ensuring(semaphore.release.commit)
    } yield result
}
object ProjectLocker {
  final case class ProjectLocked(shortcode: ProjectShortcode)
  val acquireTimeOut: Duration = 2.seconds
  val layer                    = ZLayer.fromZIO(TMap.empty[ProjectShortcode, TSemaphore].commit) >>> ZLayer.derive[ProjectLocker]
}

object ProjectLockerSpec extends ZIOSpecDefault {

  private val shortcode: ProjectShortcode                  = ProjectShortcode.unsafeFrom("0001")
  private val lock                                         = ZIO.serviceWithZIO[ProjectLocker]
  private def attemptRunWithSemaphore(zio: UIO[Int])       = lock(_.withSemaphore(shortcode)(zio))
  private def slowSuccessTask(nr: Int, duration: Duration) = ZIO.succeed(nr).delay(duration)

  val spec = suite("ProjectLocker")(
    test("should run a success task") {
      for {
        fork <- lock(_.withSemaphore(shortcode)(slowSuccessTask(1, 1.second))).fork
        _    <- TestClock.adjust(1.second)
        exit <- fork.join
      } yield assertTrue(exit == 1)
    },
    test("should prevent another task to run if lock is not acquired within acquireTimeout") {
      for {
        fork1   <- attemptRunWithSemaphore(slowSuccessTask(1, 3.seconds)).fork
        _       <- TestClock.adjust(1.second) // wait 1 seconds so that task cannot acquire lock before the other is done
        fork2   <- attemptRunWithSemaphore(slowSuccessTask(2, 3.seconds)).fork
        _       <- TestClock.adjust(10.seconds)
        result1 <- fork1.join
        exit2   <- fork2.join.exit
      } yield assertTrue(result1 == 1, exit2.isFailure)
    },
    test("should allow another task to run if lock is acquired within acquireTimeout") {
      for {
        fork1   <- attemptRunWithSemaphore(slowSuccessTask(1, 3.seconds)).fork
        _       <- TestClock.adjust(2.seconds) // wait 2 seconds so that task can acquire lock after the other is done
        fork2   <- attemptRunWithSemaphore(slowSuccessTask(2, 3.seconds)).fork
        _       <- TestClock.adjust(10.seconds)
        result1 <- fork1.join
        result2 <- fork2.join
      } yield assertTrue(result1 == 1, result2 == 2)
    },
  ).provide(ProjectLocker.layer)
}

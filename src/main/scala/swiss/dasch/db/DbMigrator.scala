package swiss.dasch.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateErrorResult
import zio.{Task, ZIO, ZLayer}

import javax.sql.DataSource

class DbMigrator(ds: DataSource) {

  def migrate(): Task[Unit] =
    ZIO
      .attempt(
        Flyway
          .configure()
          .dataSource(ds)
          .load()
          .migrate(),
      )
      .flatMap {
        case r: MigrateErrorResult => ZIO.fail(DbMigrationFailed(r.error.message, r.error.stackTrace))
        case _                     => ZIO.unit
      }
      .onError(cause => ZIO.logErrorCause("Database migration has failed", cause))

}

case class DbMigrationFailed(msg: String, stackTrace: String) extends RuntimeException(s"$msg\n$stackTrace")

object DbMigrator {
  def migrate(): ZIO[DbMigrator, Throwable, Unit] = ZIO.serviceWithZIO[DbMigrator](_.migrate())
  def layer                                       = ZLayer.derive[DbMigrator]
}

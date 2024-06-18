package swiss.dasch.util

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import swiss.dasch.config.Configuration.DbConfig
import swiss.dasch.db.{Db, DbMigrator}
import zio.{RIO, Random, ZIO, ZLayer}

import java.nio.file.{Files, Paths}
import javax.sql.DataSource

object TestDbUtil {

  type TestDbLayer = DbConfig & DataSource & Quill.Postgres[SnakeCase] & DbMigrator

  private val createTestDbConfig: ZIO[Any, Nothing, DbConfig] = for {
    uuid   <- Random.RandomLive.nextUUID
    tmpDir <- zio.System.SystemLive.propertyOrElse("java.io.tmpdir", ".").orDie
  } yield DbConfig(s"jdbc:sqlite:$tmpDir/realworld-test-$uuid.sqlite", "foo", "bar")

  private val testDbConfigLive: ZLayer[Any, Nothing, DbConfig] =
    ZLayer.scoped {
      ZIO.acquireRelease(acquire = createTestDbConfig)(release = config => clearDb(config).orDie)
    }

  private def clearDb(cfg: DbConfig): RIO[Any, Unit] = for {
    dbPath <- ZIO.succeed(Paths.get(cfg.jdbcUrl.replace("jdbc:sqlite:", "")))
    _      <- ZIO.attemptBlocking(Files.deleteIfExists(dbPath))
  } yield ()

  val testDbLayer: ZLayer[Any, Nothing, TestDbLayer] =
    testDbConfigLive >+> Db.dataSourceTest >+> Db.quillLive >+> DbMigrator.layer

  val testDbLayerWithEmptyDb: ZLayer[Any, Nothing, TestDbLayer] =
    testDbLayer >+> ZLayer.fromZIO(DbMigrator.migrate().orDie)
}

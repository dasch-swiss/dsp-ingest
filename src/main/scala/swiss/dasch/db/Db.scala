package swiss.dasch.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.*
import io.getquill.jdbczio.*
import zio.{ZIO, ZLayer}
import swiss.dasch.config.Configuration.DbConfig

import javax.sql.DataSource

object Db {

  private def create(dbConfig: DbConfig): HikariDataSource = {
    val poolConfig = new HikariConfig()
    poolConfig.setJdbcUrl(dbConfig.url)
    poolConfig.setConnectionInitSql(dbConfig.connectionInitSql)
    new HikariDataSource(poolConfig)
  }

  // Used for migration and executing queries.
  val dataSourceLive: ZLayer[DbConfig, Nothing, DataSource] =
    ZLayer.scoped {
      ZIO.fromAutoCloseable {
        for {
          dbConfig   <- ZIO.service[DbConfig]
          dataSource <- ZIO.succeed(create(dbConfig))
        } yield dataSource
      }
    }

  // Quill framework object used for specifying sql queries.
  val quillLive: ZLayer[DataSource, Nothing, Quill.Sqlite[SnakeCase]] =
    Quill.Sqlite.fromNamingStrategy(SnakeCase)
}

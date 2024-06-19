/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.*
import io.getquill.jdbczio.*
import swiss.dasch.config.Configuration.DbConfig
import zio.{ZIO, ZLayer}

import javax.sql.DataSource

object Db {

  private def create(dbConfig: DbConfig): HikariDataSource = {
    val poolConfig = new HikariConfig()
    poolConfig.setDriverClassName("org.postgresql.Driver")
    poolConfig.setJdbcUrl(dbConfig.jdbcUrl)
    poolConfig.setUsername(dbConfig.username)
    poolConfig.setPassword(dbConfig.password)
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

  val quillLive: ZLayer[DataSource, Nothing, Quill.Postgres[SnakeCase]] =
    Quill.Postgres.fromNamingStrategy(SnakeCase)

}

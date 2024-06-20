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

  private def makeDataSource(dbConfig: DbConfig): HikariDataSource = {
    val config = new HikariConfig()
    config.setDriverClassName("org.postgresql.Driver")
    config.setJdbcUrl(dbConfig.jdbcUrl)
    config.setUsername(dbConfig.username)
    config.setPassword(dbConfig.password)
    new HikariDataSource(config)
  }

  val dataSourceLive: ZLayer[DbConfig, Nothing, DataSource] =
    ZLayer.scoped(ZIO.fromAutoCloseable(ZIO.serviceWith[DbConfig](makeDataSource)))

  val quillLive: ZLayer[DataSource, Nothing, Quill.Postgres[SnakeCase]] =
    Quill.Postgres.fromNamingStrategy(SnakeCase)
}

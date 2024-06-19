/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import zio.{ZIO, ZLayer}

import javax.sql.DataSource

object DbTest {
  private def createTestDs(): HikariDataSource = {
    val poolConfig = new HikariConfig()
    poolConfig.setJdbcUrl("jdbc:h2:mem:test_db:MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
    poolConfig.setDriverClassName("org.h2.Driver")
    new HikariDataSource(poolConfig)
  }

  // Used for migration and executing queries.
  val dataSource: ZLayer[Any, Nothing, DataSource] =
    ZLayer.scoped(ZIO.fromAutoCloseable(ZIO.succeed(createTestDs())))
}

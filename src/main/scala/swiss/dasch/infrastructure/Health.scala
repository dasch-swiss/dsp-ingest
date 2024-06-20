/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.infrastructure.Health.Status
import swiss.dasch.infrastructure.Health.Status.*
import zio.json.{DeriveJsonCodec, DeriveJsonEncoder, JsonCodec, JsonEncoder}

import scala.util.Try

final case class Health(status: Status) {
  def isHealthy(): Boolean = status == UP
}

object Health {
  enum Status {
    case UP   extends Status
    case DOWN extends Status
  }

  object Status {
    given encoder: JsonCodec[Status] = JsonCodec[String].transformOrFail(
      str => Try(Status.valueOf(str)).toEither.left.map(_.getMessage),
      _.toString,
    )
  }

  given encoder: JsonCodec[Health] = DeriveJsonCodec.gen[Health]
  def up(): Health                 = Health(UP)
  def down(): Health               = Health(DOWN)
}

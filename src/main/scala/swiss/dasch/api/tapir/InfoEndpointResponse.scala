/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.tapir

import swiss.dasch.version.BuildInfo
import zio.json.{ DeriveJsonCodec, JsonCodec }

case class InfoEndpointResponse(
    name: String = BuildInfo.name,
    version: String = BuildInfo.version,
    scalaVersion: String = BuildInfo.scalaVersion,
    sbtVersion: String = BuildInfo.sbtVersion,
    buildTime: String = BuildInfo.builtAtString,
    gitCommit: String = BuildInfo.gitCommit,
  )

object InfoEndpointResponse {

  val instance                                = InfoEndpointResponse()
  given code: JsonCodec[InfoEndpointResponse] = DeriveJsonCodec.gen[InfoEndpointResponse]
}

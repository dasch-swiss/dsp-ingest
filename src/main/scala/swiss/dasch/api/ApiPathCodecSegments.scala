/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import zio.http.codec.HttpCodec.string
import zio.http.codec.PathCodec

object ApiPathCodecSegments {
  // project paths
  val projects: PathCodec[Unit] = "projects"
  // sipi paths
  val sipi: PathCodec[Unit]     = "sipi"
  val help: PathCodec[Unit]     = "help"

  // project path variables
  val shortcodePathVarStr                 = "shortcode"
  val shortcodePathVar: PathCodec[String] = string(shortcodePathVarStr)

  // sipi path variables
  val commandPathVar: PathCodec[String] = string("command")
}

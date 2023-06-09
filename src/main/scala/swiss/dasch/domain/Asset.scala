/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import zio.nio.file.Path

opaque type AssetId = String Refined MatchesRegex["^[a-zA-Z0-9-]{4,}$"]
object AssetId {
  def make(id: String): Either[String, AssetId] = refineV(id)
}

final case class Asset(id: AssetId, belongsToProject: ProjectShortcode)

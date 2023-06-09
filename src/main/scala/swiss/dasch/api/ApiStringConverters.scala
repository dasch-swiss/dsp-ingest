/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.shortcodePathVarStr
import swiss.dasch.domain.ProjectShortcode
import zio.{ IO, ZIO }

object ApiStringConverters {
  def fromPathVarToProjectShortcode(value: String, pathVariableName: String = shortcodePathVarStr)
      : IO[IllegalArguments, ProjectShortcode] =
    ZIO
      .fromEither(ProjectShortcode.make(value))
      .mapError(ApiProblem.invalidPathVariable(pathVariableName, value, _))
}

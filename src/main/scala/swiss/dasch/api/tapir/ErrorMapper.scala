/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.tapir

import swiss.dasch.api.ApiProblem
import zio.IO
object ErrorMapper {
  def defaultErrorsMappings[E <: Throwable, A](io: IO[E, A]): IO[ApiProblem, A] = io.mapError {
    case e => ApiProblem.InternalServerError(e)
  }
}

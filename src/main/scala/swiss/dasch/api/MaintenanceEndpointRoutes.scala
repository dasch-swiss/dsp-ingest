/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.MaintenanceEndpoint.*
import swiss.dasch.domain.*

object MaintenanceEndpointRoutes {

  private val needsOriginalsRoute = needsOriginalsEndpoint.implement { imagesOnlyMaybe =>
    MaintenanceActions
      .createNeedsOriginalsReport(imagesOnlyMaybe.getOrElse(true))
      .forkDaemon
      .logError
      .as("work in progress")
  }

  private val needsTopLeftCorrectionRoute =
    needsTopLeftCorrectionEndpoint.implement(_ =>
      MaintenanceActions.createNeedsTopLeftCorrectionReport().forkDaemon.logError.as("work in progress")
    )

  val app = (needsOriginalsRoute ++ needsTopLeftCorrectionRoute).toApp
}

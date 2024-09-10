/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import zio.*
import swiss.dasch.domain.AssetInfo

class FetchAssetPermissionsMock extends FetchAssetPermissions {
  def getPermissionCode(
    jwt: String,
    assetInfo: AssetInfo,
  ): Task[Int] =
    ZIO.succeed(2)
}

object FetchAssetPermissionsMock {
  val layer = ZLayer.derive[FetchAssetPermissionsMock]
}

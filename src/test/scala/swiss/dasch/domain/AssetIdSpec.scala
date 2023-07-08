/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.test.*

object AssetIdSpec extends ZIOSpecDefault {

  val spec = suite("AssetIdSpec")(
    test("AssetId should be created from a valid string") {
      val valid = Gen.stringBounded(1, 20)(Gen.oneOf(Gen.alphaNumericChar, Gen.const('-')))
      check(valid) { s =>
        assertTrue(AssetId.make(s).exists(_.toString == s))
      }
    },
    test("AssetId should not be created from an empty string") {
      assertTrue(AssetId.make("").isLeft)
    },
    test("AssetId should not be created from an string containing invalid characters") {
      val invalid = Gen.stringBounded(1, 20)(
        Gen.fromIterable(List('/', '!', '$', '%', '&', '(', ')', '=', '?', ' ', '+', '*', '#', '@', '€', '£', '§'))
      )
      check(invalid) { c =>
        assertTrue(AssetId.make(c).isLeft)
      }
    },
  )
}

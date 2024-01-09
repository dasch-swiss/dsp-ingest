package swiss.dasch.domain

import zio.Scope
import zio.test.*

object CsvUtilSpec extends ZIOSpecDefault {

  private val escapeCsvValueSuite = suite("escapeCsvValue")(
    test("Should not escape a value without special characters") {
      val result   = CsvUtil.escapeCsvValue("test")
      val expected = "test"
      assertTrue(result == expected)
    },
    test("Should escape a value with a comma") {
      val result   = CsvUtil.escapeCsvValue("test,test")
      val expected = "\"test,test\""
      assertTrue(result == expected)
    },
    test("Should escape a value with a double quote") {
      val result   = CsvUtil.escapeCsvValue("test\"test")
      val expected = "\"test\"\"test\""
      assertTrue(result == expected)
    },
  )

  def spec: Spec[TestEnvironment with Scope, Any] = suite("CsvUtilSpec")(escapeCsvValueSuite)

}

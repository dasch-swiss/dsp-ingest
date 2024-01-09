package swiss.dasch.domain

object CsvUtil {
  def escapeCsvValue(value: String): String =
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) s"\"${value.replace("\"", "\"\"")}\""
    else value
}

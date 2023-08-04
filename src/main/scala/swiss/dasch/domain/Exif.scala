package swiss.dasch.domain

// see also https://exiftool.org/TagNames/EXIF.html
object Exif {
  object Image {
    val Orientation = "Exif.Image.Orientation"

    sealed trait OrientationValue { def value: Char }
    object OrientationValue       {
      // = Horizontal(normal)
      case object Horizontal extends OrientationValue { val value = '1' }

      // = Rotate 270 CW
      case object Rotate270CW extends OrientationValue { val value = '8' }
    }
  }
}

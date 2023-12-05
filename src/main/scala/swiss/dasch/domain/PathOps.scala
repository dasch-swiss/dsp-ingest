package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import zio.nio.file.Path

object PathOps {
  extension (path: Path) {
    def fileExtension: String =
      Option(FilenameUtils.getExtension(path.filename.toString)).getOrElse("")
  }
}

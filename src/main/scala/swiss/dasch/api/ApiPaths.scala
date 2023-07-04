package swiss.dasch.api

import zio.http.codec.HttpCodec.string
import zio.http.codec.PathCodec

object ApiPaths {
  val projects: PathCodec[Unit]           = "projects"
  val shortcodePathVar: PathCodec[String] = string("shortcode")
}

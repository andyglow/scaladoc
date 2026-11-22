package scaladoc.sbtplugin

import sbt._

private[sbtplugin] object PlatformCompat {

  /** The cross version of the scaladoc libraries. sbt 2's `%%` follows `platform`, which Scala.js sets to `sjs1`. */
  val libraryCrossVersion: Def.Initialize[CrossVersion] = Def.setting(CrossVersion.binary)
}

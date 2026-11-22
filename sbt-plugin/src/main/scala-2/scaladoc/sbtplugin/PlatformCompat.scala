package scaladoc.sbtplugin

import sbt._

private[sbtplugin] object PlatformCompat {

  // sbt 1's `%%` always picks the JVM artifact. Scala.js (through sbt-platform-deps) defines this key with its
  // `_sjs1_` cross version, the one `%%%` uses; keys are matched by name and type, so this reads that setting
  // without depending on Scala.js
  private val platformDepsCrossVersion = SettingKey[CrossVersion]("platformDepsCrossVersion")

  /** The cross version of the scaladoc libraries: `_sjs1_<scala>` on Scala.js, `_<scala>` on the JVM. */
  val libraryCrossVersion: Def.Initialize[CrossVersion] =
    Def.setting(platformDepsCrossVersion.?.value.getOrElse(CrossVersion.binary))
}

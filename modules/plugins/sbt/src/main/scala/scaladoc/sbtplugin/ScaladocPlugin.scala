package scaladoc.sbtplugin

import scala.annotation.nowarn

import sbt._
import sbt.Keys._

/** Sets the Scala compiler up for scaladoc. Enable it on the modules that need it with `enablePlugins(ScaladocPlugin)`.
  *
  *   - '''Scala 2:''' the compiler plugin, which embeds the scaladoc of the module's classes into their bytecode; the
  *     `scaladocInclude*` / `scaladocExclude*` settings become its options.
  *   - '''Scala 3:''' `-Yread-docs`, so that `Scaladoc.of` also reads doc comments from other modules and jars. Scala 3
  *     keeps doc comments in TASTy on its own, so nothing else is needed.
  *
  * It adds no library: a module that reads scaladoc with `Scaladoc.of` depends on `scaladoc-parser` itself, like on any
  * library it calls. On Scala.js the compiler plugin is the JVM artifact too, as it runs inside scalac.
  */
object ScaladocPlugin extends AutoPlugin {

  override def requires = plugins.JvmPlugin

  override def trigger = noTrigger

  object autoImport {
    val scaladocVersion = settingKey[String]("Version of the scaladoc compiler plugin")
    @deprecated("a module that shouldn't embed scaladoc doesn't enable the plugin", "0.5.1")
    val scaladocEmbed =
      settingKey[Boolean]("Deprecated: Scala 2: embed scaladoc into bytecode; don't enable the plugin instead")
    @deprecated(
      "add scaladoc-parser as a dependency instead: libraryDependencies += \"io.github.andyglow\" %% \"scaladoc-parser\" % <version>",
      "0.5.1")
    val scaladocRead =
      settingKey[Boolean]("Deprecated: adds scaladoc-core and scaladoc-parser; depend on scaladoc-parser instead")
    val scaladocIncludePaths =
      settingKey[Seq[String]]("Scala 2: embed only classes from source files matching these globs")
    val scaladocExcludePaths =
      settingKey[Seq[String]]("Scala 2: don't embed classes from source files matching these globs")
    val scaladocIncludeClasses =
      settingKey[Seq[String]]("Scala 2: embed only classes whose fully-qualified name matches these patterns")
    val scaladocExcludeClasses =
      settingKey[Seq[String]]("Scala 2: don't embed classes whose fully-qualified name matches these patterns")
  }

  import autoImport._

  private val organization = "io.github.andyglow"

  private val compilerPluginName = "scaladoc-compiler-plugin"

  /** The `-P:` scalac options for the given include/exclude patterns. */
  def compilerPluginOptions(
    includePaths: Seq[String],
    excludePaths: Seq[String],
    includeClasses: Seq[String],
    excludeClasses: Seq[String]): Seq[String] = {
    def opts(kind: String, patterns: Seq[String]) = patterns.map(p => s"-P:$compilerPluginName:$kind:$p")
    opts("include:path", includePaths) ++ opts("exclude:path", excludePaths) ++
      opts("include:class", includeClasses) ++ opts("exclude:class", excludeClasses)
  }

  private def isScala3(binaryVersion: String): Boolean = binaryVersion.startsWith("3")

  @nowarn("cat=deprecation")
  override def projectSettings = Seq(
    scaladocVersion        := BuildInfo.version,
    scaladocEmbed          := true,
    scaladocRead           := false,
    scaladocIncludePaths   := Nil,
    scaladocExcludePaths   := Nil,
    scaladocIncludeClasses := Nil,
    scaladocExcludeClasses := Nil,
    libraryDependencies ++= {
      val v      = scaladocVersion.value
      val scala3 = isScala3(scalaBinaryVersion.value)
      // the compiler plugin runs in scalac, so it is the JVM artifact on Scala.js too, where `%%` (which follows
      // `platform`) would pick a `_sjs1` one that doesn't exist. Its jar has everything it needs.
      val plugin =
        // deprecated: until 0.6.0, `scaladocEmbed := false` still leaves the compiler plugin out
        if (!scala3 && scaladocEmbed.value)
          Seq(compilerPlugin(organization % s"${compilerPluginName}_${scalaBinaryVersion.value}" % v))
        else Nil
      // deprecated: until 0.6.0, `scaladocRead := true` still adds the libraries
      val libraries =
        if (scaladocRead.value) {
          val cross = PlatformCompat.libraryCrossVersion.value
          Seq((organization % "scaladoc-core" % v).cross(cross), (organization % "scaladoc-parser" % v).cross(cross))
        } else Nil
      plugin ++ libraries
    },
    scalacOptions ++= {
      if (isScala3(scalaBinaryVersion.value)) Seq("-Yread-docs")
      else if (scaladocEmbed.value)
        compilerPluginOptions(
          scaladocIncludePaths.value,
          scaladocExcludePaths.value,
          scaladocIncludeClasses.value,
          scaladocExcludeClasses.value)
      else Nil
    }
  )
}

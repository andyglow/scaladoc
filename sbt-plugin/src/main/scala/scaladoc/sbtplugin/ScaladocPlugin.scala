package scaladoc.sbtplugin

import sbt._
import sbt.Keys._

/** Sets a project up for scaladoc. Enable it on the modules that need it with `enablePlugins(ScaladocPlugin)`.
  *
  * A module can publish its scaladoc, read scaladoc with `Scaladoc.of`, or both, and gets only what that takes:
  *
  *   - '''publishing''' (`scaladocEmbed`, on by default): on Scala 2, the compiler plugin, which embeds the scaladoc of
  *     the module's classes into their bytecode, and nothing else; the `scaladocInclude*` / `scaladocExclude*` settings
  *     become its options. Scala 3 keeps doc comments in TASTy on its own, so there it adds nothing.
  *   - '''reading''' (`scaladocRead`, off by default): `scaladoc-ast` and `scaladoc-parser`, and on Scala 3
  *     `-Yread-docs`, so that `Scaladoc.of` also reads doc comments from other modules and jars.
  *
  * On Scala.js projects the libraries are the `_sjs1` ones; the compiler plugin is always the JVM artifact.
  */
object ScaladocPlugin extends AutoPlugin {

  override def requires = plugins.JvmPlugin

  override def trigger = noTrigger

  object autoImport {
    val scaladocVersion = settingKey[String]("Version of the scaladoc libraries and compiler plugin")
    val scaladocEmbed   = settingKey[Boolean]("Scala 2: embed scaladoc into bytecode with the compiler plugin")
    val scaladocRead    =
      settingKey[Boolean]("Read scaladoc with Scaladoc.of: adds scaladoc-ast and scaladoc-parser, and -Yread-docs on Scala 3")
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
      val cross  = PlatformCompat.libraryCrossVersion.value
      // the compiler plugin runs in scalac, so it is the JVM artifact on Scala.js too, where `%%` (which follows
      // `platform`) would pick a `_sjs1` one that doesn't exist. Its jar has everything it needs, so the module needs
      // nothing else to publish its scaladoc.
      val plugin =
        if (!scala3 && scaladocEmbed.value)
          Seq(compilerPlugin(organization % s"${compilerPluginName}_${scalaBinaryVersion.value}" % v))
        else Nil
      val libraries =
        if (scaladocRead.value)
          Seq((organization % "scaladoc-ast" % v).cross(cross), (organization % "scaladoc-parser" % v).cross(cross))
        else Nil
      plugin ++ libraries
    },
    scalacOptions ++= {
      val scala3 = isScala3(scalaBinaryVersion.value)
      if (scala3) { if (scaladocRead.value) Seq("-Yread-docs") else Nil }
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

package scaladoc.sbtplugin

import sbt._
import sbt.Keys._

/** Sets the Scala compiler up for scaladoc. Enable it on the modules that need it with `enablePlugins(ScaladocPlugin)`.
  *
  *   - '''Scala 2:''' the compiler plugin, which embeds the scaladoc of the module's classes into their bytecode; the
  *     `scaladocInclude*` / `scaladocExclude*` settings become its options. The module depends on `scaladoc-annotation`,
  *     the one-class jar of the annotation that holds it: its classes refer to it, and Scala 3 modules that use them
  *     don't compile without it.
  *   - '''Scala 3:''' `-Xread-docs` (`-Yread-docs` before Scala 3.5), so that `Scaladoc.of` also reads doc comments
  *     from other modules and jars. Scala 3 keeps doc comments in TASTy on its own, so nothing else is needed.
  *
  * It adds no library for reading: a module that reads scaladoc with `Scaladoc.of` depends on `scaladoc-parser` itself,
  * like on any library it calls. On Scala.js the compiler plugin is the JVM artifact too, as it runs inside scalac.
  */
object ScaladocPlugin extends AutoPlugin {

  override def requires = plugins.JvmPlugin

  override def trigger = noTrigger

  object autoImport {
    val scaladocVersion = settingKey[String]("Version of the scaladoc compiler plugin")
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

  private val annotationName = "scaladoc-annotation"

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

  /** The option that makes Scala 3 read doc comments from TASTy: `-Xread-docs`, or `-Yread-docs` before Scala 3.5,
    * which named it so. Each version takes only its own name without a warning, and the older ones ignore the newer
    * name, a warning that fails builds with `-Werror`.
    */
  def readDocsOption(scalaVersion: String): String = CrossVersion.partialVersion(scalaVersion) match {
    case Some((3, minor)) if minor < 5 => "-Yread-docs"
    case _                             => "-Xread-docs"
  }

  override def projectSettings = Seq(
    scaladocVersion        := BuildInfo.version,
    scaladocIncludePaths   := Nil,
    scaladocExcludePaths   := Nil,
    scaladocIncludeClasses := Nil,
    scaladocExcludeClasses := Nil,
    libraryDependencies ++= {
      val v      = scaladocVersion.value
      val scala3 = isScala3(scalaBinaryVersion.value)
      // the compiler plugin runs in scalac, so it is the JVM artifact on Scala.js too, where `%%` (which follows
      // `platform`) would pick a `_sjs1` one that doesn't exist. Its jar has everything it needs to run. The classes it
      // embeds scaladoc into refer to the annotation in their Scala signatures, so the annotation's jar is a dependency
      // of the module, and of everything that uses it: Scala 3 doesn't compile against those classes without it.
      if (scala3) Nil
      else
        Seq(
          compilerPlugin(organization % s"${compilerPluginName}_${scalaBinaryVersion.value}" % v),
          organization % annotationName % v
        )
    },
    scalacOptions ++= {
      if (isScala3(scalaBinaryVersion.value)) Seq(readDocsOption(scalaVersion.value))
      else
        compilerPluginOptions(
          scaladocIncludePaths.value,
          scaladocExcludePaths.value,
          scaladocIncludeClasses.value,
          scaladocExcludeClasses.value)
    }
  )
}

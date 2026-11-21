package scaladoc.sbtplugin

import sbt._
import sbt.Keys._

/** Sets a project up for scaladoc. Enable it on the modules that need it with `enablePlugins(ScaladocPlugin)`.
  *
  * On Scala 2 it adds the scaladoc compiler plugin, which embeds the scaladoc of classes into their bytecode, together
  * with `scaladoc-ast` and `scaladoc-parser` (`Provided`). The `scaladocInclude*` / `scaladocExclude*` settings become
  * the compiler plugin's include/exclude options.
  *
  * The compiler plugin is Scala 2 only. On Scala 3 the plugin adds `scaladoc-ast`, `scaladoc-parser` and
  * `-Yread-docs`, so that `Scaladoc.of` also reads doc comments from other modules and jars.
  */
object ScaladocPlugin extends AutoPlugin {

  override def requires = plugins.JvmPlugin

  override def trigger = noTrigger

  object autoImport {
    val scaladocVersion = settingKey[String]("Version of the scaladoc libraries and compiler plugin")
    val scaladocEmbed   = settingKey[Boolean]("Scala 2: embed scaladoc into bytecode with the compiler plugin")
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
    scaladocIncludePaths   := Nil,
    scaladocExcludePaths   := Nil,
    scaladocIncludeClasses := Nil,
    scaladocExcludeClasses := Nil,
    libraryDependencies ++= {
      val v      = scaladocVersion.value
      val scala3 = isScala3(scalaBinaryVersion.value)
      val embed  = scaladocEmbed.value
      val ast    = organization %% "scaladoc-ast"    % v
      val parser = organization %% "scaladoc-parser" % v
      if (scala3 || !embed) Seq(ast, parser)
      else Seq(compilerPlugin(organization %% compilerPluginName % v), ast, parser % Provided)
    },
    scalacOptions ++= {
      val scala3  = isScala3(scalaBinaryVersion.value)
      val embed   = scaladocEmbed.value
      val options = compilerPluginOptions(
        scaladocIncludePaths.value,
        scaladocExcludePaths.value,
        scaladocIncludeClasses.value,
        scaladocExcludeClasses.value)
      if (scala3) Seq("-Yread-docs")
      else if (embed) options
      else Nil
    }
  )
}

ThisBuild / scalaVersion       := "2.13.18"
ThisBuild / crossScalaVersions := Seq("2.13.18", "2.12.21", "3.3.9")

// Scala.js: on Scala 2 `model` publishes its scaladoc with the (JVM) compiler plugin, which embeds it into its
// classes; on Scala 3 its TASTy keeps it. `app` reads it with `Scaladoc.of`, for which it gets the `_sjs1` libraries
// (and `-Yread-docs` on Scala 3).
lazy val model = project
  .enablePlugins(ScalaJSPlugin, ScaladocPlugin)

lazy val app = project
  .dependsOn(model)
  .enablePlugins(ScalaJSPlugin, ScaladocPlugin)
  .settings(
    scaladocEmbed                   := false,
    scaladocRead                    := true,
    scalaJSUseMainModuleInitializer := true
  )

TaskKey[Unit]("checkDependencies") := {
  def resolved(update: UpdateReport) = update.allModules.filter(_.organization == "io.github.andyglow").map(_.name)
  // the reader gets the libraries for Scala.js
  val reader = resolved((app / Compile / update).value)
  assert(reader.exists(_.startsWith("scaladoc-parser_sjs1_")), s"app resolved: $reader")
  assert(reader.exists(_.startsWith("scaladoc-ast_sjs1_")), s"app resolved: $reader")
  // the publisher gets the (JVM) compiler plugin on Scala 2, and no libraries
  val publisher      = resolved((model / Compile / update).value)
  val compilerPlugin = s"scaladoc-compiler-plugin_${scalaBinaryVersion.value}"
  val expected       = if (scalaBinaryVersion.value.startsWith("2")) Seq(compilerPlugin) else Nil
  assert(publisher == expected, s"model resolved: $publisher")
}

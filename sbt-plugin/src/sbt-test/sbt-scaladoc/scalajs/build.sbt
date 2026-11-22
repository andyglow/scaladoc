ThisBuild / scalaVersion       := "2.13.18"
ThisBuild / crossScalaVersions := Seq("2.13.18", "2.12.21", "3.3.9")

// Scala.js: sbt-scaladoc adds the `_sjs1` libraries, and on Scala 2 the (JVM) compiler plugin, which embeds the
// scaladoc of `model`'s classes; on Scala 3 `app` reads it from `model`'s TASTy with `-Yread-docs`
lazy val model = project
  .enablePlugins(ScalaJSPlugin, ScaladocPlugin)

lazy val app = project
  .dependsOn(model)
  .enablePlugins(ScalaJSPlugin, ScaladocPlugin)
  .settings(
    scaladocEmbed                   := false,
    scalaJSUseMainModuleInitializer := true
  )

TaskKey[Unit]("checkDependencies") := {
  val deps = (app / libraryDependencies).value ++ (model / libraryDependencies).value
  val ours = deps.filter(_.organization == "io.github.andyglow")
  assert(ours.nonEmpty, s"libraryDependencies: $deps")
  // resolved names: the libraries for Scala.js, the compiler plugin for the JVM
  val resolved = (model / Compile / update).value.allModules.filter(_.organization == "io.github.andyglow").map(_.name)
  assert(resolved.exists(_.startsWith("scaladoc-parser_sjs1_")), s"resolved: $resolved")
  assert(resolved.exists(_.startsWith("scaladoc-ast_sjs1_")), s"resolved: $resolved")
  val compilerPlugin = s"scaladoc-compiler-plugin_${scalaBinaryVersion.value}"
  if (scalaBinaryVersion.value.startsWith("2")) assert(resolved.contains(compilerPlugin), s"resolved: $resolved")
}

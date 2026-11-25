ThisBuild / scalaVersion       := "2.13.18"
ThisBuild / crossScalaVersions := Seq("2.13.18", "2.12.21", "3.9.0")

// Scala.js: on Scala 2 `model` publishes its scaladoc with the (JVM) compiler plugin, which embeds it into its
// classes; on Scala 3 its TASTy keeps it. `app` reads it with `Scaladoc.of`: it depends on the `_sjs1` parser itself,
// and the plugin adds `-Xread-docs` on Scala 3 (on Scala 2 it embeds `app`'s own scaladoc, which does no harm).
lazy val model = project
  .enablePlugins(ScalaJSPlugin, ScaladocPlugin)

lazy val app = project
  .dependsOn(model)
  .enablePlugins(ScalaJSPlugin, ScaladocPlugin)
  .settings(
    // the `_sjs1` parser, spelled out so the build loads on sbt 1 (which has `%%%`) and sbt 2 (whose `%%` picks it)
    libraryDependencies += "io.github.andyglow" % s"scaladoc-parser_sjs1_${scalaBinaryVersion.value}" % scaladocVersion.value,
    scalaJSUseMainModuleInitializer := true
  )

TaskKey[Unit]("checkDependencies") := {
  def resolved(update: UpdateReport) = update.allModules.filter(_.organization == "io.github.andyglow").map(_.name)
  // the reader gets the libraries for Scala.js
  val reader = resolved((app / Compile / update).value)
  assert(reader.exists(_.startsWith("scaladoc-parser_sjs1_")), s"app resolved: $reader")
  assert(reader.exists(_.startsWith("scaladoc-core_sjs1_")), s"app resolved: $reader")
  // the publisher gets the (JVM) compiler plugin on Scala 2, and scaladoc-annotation, which its classes refer to
  val publisher      = resolved((model / Compile / update).value)
  val compilerPlugin = s"scaladoc-compiler-plugin_${scalaBinaryVersion.value}"
  val expected       = if (scalaBinaryVersion.value.startsWith("2")) Seq(compilerPlugin, "scaladoc-annotation") else Nil
  assert(publisher.sorted == expected.sorted, s"model resolved: $publisher")
}

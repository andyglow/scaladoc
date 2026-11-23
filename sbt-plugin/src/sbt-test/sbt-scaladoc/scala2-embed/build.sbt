ThisBuild / scalaVersion       := "2.13.18"
ThisBuild / crossScalaVersions := Seq("2.13.18", "2.12.21")

// publishes: embeds the scaladoc of its classes, except `demo.model.internal.*`, and gets nothing but the compiler plugin
lazy val model = project
  .enablePlugins(ScaladocPlugin)
  .settings(scaladocExcludeClasses := Seq("demo.model.internal.*"))

// reads it: `model` is on its class path as compiled classes, so `Scaladoc.of` only sees embedded scaladoc
lazy val app = project
  .dependsOn(model)
  .enablePlugins(ScaladocPlugin)
  .settings(
    scaladocEmbed := false,
    scaladocRead  := true
  )

// a module that only publishes depends on none of the scaladoc libraries, not even at compile time
TaskKey[Unit]("checkModel") := {
  val deps = (model / libraryDependencies).value.filter(_.organization == "io.github.andyglow")
  assert(deps.map(_.name) == Seq("scaladoc-compiler-plugin_" + scalaBinaryVersion.value), s"libraryDependencies: $deps")
  val classpath = (model / Compile / dependencyClasspath).value.map(_.data.toString)
  assert(!classpath.exists(_.contains("scaladoc-")), s"classpath: $classpath")
}

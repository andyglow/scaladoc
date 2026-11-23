ThisBuild / scalaVersion       := "2.13.18"
ThisBuild / crossScalaVersions := Seq("2.13.18", "2.12.21")

// publishes: embeds the scaladoc of its classes, except `demo.model.internal.*`, and gets nothing but the compiler plugin
lazy val model = project
  .enablePlugins(ScaladocPlugin)
  .settings(scaladocExcludeClasses := Seq("demo.model.internal.*"))

// reads it: `model` is on its class path as compiled classes, so `Scaladoc.of` only sees embedded scaladoc. On Scala 2
// a reader needs no compiler setup, so it doesn't enable the plugin: it depends on the parser like on any library
lazy val app = project
  .dependsOn(model)
  .settings(libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % sys.props("plugin.version"))

// a module that only publishes depends on none of the scaladoc libraries, not even at compile time
TaskKey[Unit]("checkModel") := {
  val deps = (model / libraryDependencies).value.filter(_.organization == "io.github.andyglow")
  assert(deps.map(_.name) == Seq("scaladoc-compiler-plugin_" + scalaBinaryVersion.value), s"libraryDependencies: $deps")
  val classpath = (model / Compile / dependencyClasspath).value.map(_.data.toString)
  assert(!classpath.exists(_.contains("scaladoc-")), s"classpath: $classpath")
}

// deprecated, until 0.6.0: `scaladocEmbed := false` still leaves the compiler plugin out
lazy val legacy = project
  .enablePlugins(ScaladocPlugin)
  .settings(scaladocEmbed := false)

TaskKey[Unit]("checkLegacy") := {
  val deps = (legacy / libraryDependencies).value.filter(_.organization == "io.github.andyglow")
  assert(deps.isEmpty, s"libraryDependencies: $deps")
  val opts = (legacy / Compile / scalacOptions).value.filter(_.startsWith("-P:scaladoc"))
  assert(opts.isEmpty, s"scalacOptions: $opts")
}

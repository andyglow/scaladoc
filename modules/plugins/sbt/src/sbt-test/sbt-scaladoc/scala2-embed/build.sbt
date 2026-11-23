ThisBuild / scalaVersion       := "2.13.18"
ThisBuild / crossScalaVersions := Seq("2.13.18", "2.12.21")

// publishes: embeds the scaladoc of its classes, except `demo.model.internal.*`. It gets the compiler plugin, and
// scaladoc-annotation, which its classes refer to
lazy val model = project
  .enablePlugins(ScaladocPlugin)
  .settings(scaladocExcludeClasses := Seq("demo.model.internal.*"))

// reads it: `model` is on its class path as compiled classes, so `Scaladoc.of` only sees embedded scaladoc. On Scala 2
// a reader needs no compiler setup, so it doesn't enable the plugin: it depends on the parser like on any library
lazy val app = project
  .dependsOn(model)
  .settings(libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % sys.props("plugin.version"))

// uses `model`'s classes from Scala 3, as a Scala 3 project uses a 2.13 library. It compiles because `model` brings
// scaladoc-annotation along: Scala 3 fails on the annotation in their signatures without it ("Bad symbolic
// reference"). And it reads their scaladoc with `Scaladoc.of`, which on Scala 3 finds it in that annotation
lazy val consumer3 = project
  .dependsOn(model)
  .settings(
    scalaVersion       := "3.3.9",
    crossScalaVersions := Seq("3.3.9"),
    libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % sys.props("plugin.version")
  )

// a module that only publishes depends on scaladoc-annotation, and none of the scaladoc libraries
TaskKey[Unit]("checkModel") := {
  val deps = (model / libraryDependencies).value.filter(_.organization == "io.github.andyglow")
  assert(
    deps.map(_.name) == Seq("scaladoc-compiler-plugin_" + (model / scalaBinaryVersion).value, "scaladoc-annotation"),
    s"libraryDependencies: $deps")
  val classpath = (model / Compile / dependencyClasspath).value.map(_.data.toString).filter(_.contains("scaladoc-"))
  assert(classpath.size == 1 && classpath.head.contains("scaladoc-annotation"), s"classpath: $classpath")
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

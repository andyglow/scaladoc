ThisBuild / scalaVersion := "3.9.0"

// publishes: on Scala 3 the doc comments go into TASTy anyway, so the plugin adds no library. It's on the 3.3 LTS,
// which takes `-Yread-docs` and only warns about `-Xread-docs`, a warning that `-Werror` makes an error
lazy val model = project
  .enablePlugins(ScaladocPlugin)
  .settings(scalaVersion := "3.3.7", scalacOptions += "-Werror")

// reads: the plugin adds -Xread-docs, which makes `Scaladoc.of` read doc comments from `model`'s TASTy, the 3.3 one,
// and the module depends on the parser itself
lazy val app = project
  .dependsOn(model)
  .enablePlugins(ScaladocPlugin)
  .settings(libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % scaladocVersion.value)

TaskKey[Unit]("checkSettings") := {
  val opts = (app / Compile / scalacOptions).value
  val deps = (app / libraryDependencies).value
  assert(opts.contains("-Xread-docs"), s"scalacOptions: $opts")
  assert(!deps.exists(_.name.startsWith("scaladoc-compiler-plugin")), s"libraryDependencies: $deps")
  assert(deps.exists(_.name == "scaladoc-parser"), s"libraryDependencies: $deps")
  val modelDeps = (model / libraryDependencies).value.filter(_.organization == "io.github.andyglow")
  assert(modelDeps.isEmpty, s"model libraryDependencies: $modelDeps")
  val modelOpts = (model / Compile / scalacOptions).value
  assert(modelOpts.contains("-Yread-docs") && !modelOpts.contains("-Xread-docs"), s"model scalacOptions: $modelOpts")
}

ThisBuild / scalaVersion := "3.3.9"

// publishes: on Scala 3 the doc comments go into TASTy anyway, so the plugin adds nothing
lazy val model = project
  .enablePlugins(ScaladocPlugin)

// reads: the plugin adds the libraries and -Yread-docs, which makes `Scaladoc.of` read doc comments from `model`'s TASTy
lazy val app = project
  .dependsOn(model)
  .enablePlugins(ScaladocPlugin)
  .settings(scaladocRead := true)

TaskKey[Unit]("checkSettings") := {
  val opts = (app / Compile / scalacOptions).value
  val deps = (app / libraryDependencies).value
  assert(opts.contains("-Yread-docs"), s"scalacOptions: $opts")
  assert(!deps.exists(_.name.startsWith("scaladoc-compiler-plugin")), s"libraryDependencies: $deps")
  assert(deps.exists(_.name == "scaladoc-parser"), s"libraryDependencies: $deps")
  val modelDeps = (model / libraryDependencies).value.filter(_.organization == "io.github.andyglow")
  assert(modelDeps.isEmpty, s"model libraryDependencies: $modelDeps")
  assert(!(model / Compile / scalacOptions).value.contains("-Yread-docs"), "model doesn't read")
}

ThisBuild / scalaVersion := "3.3.9"

// no plugin: on Scala 3 the doc comments go into TASTy anyway
lazy val model = project

// the plugin adds scaladoc-parser and -Yread-docs, which makes `Scaladoc.of` read doc comments from `model`'s TASTy
lazy val app = project
  .dependsOn(model)
  .enablePlugins(ScaladocPlugin)

TaskKey[Unit]("checkSettings") := {
  val opts = (app / Compile / scalacOptions).value
  val deps = (app / libraryDependencies).value
  assert(opts.contains("-Yread-docs"), s"scalacOptions: $opts")
  assert(!deps.exists(_.name.startsWith("scaladoc-compiler-plugin")), s"libraryDependencies: $deps")
  assert(deps.exists(_.name == "scaladoc-parser"), s"libraryDependencies: $deps")
}

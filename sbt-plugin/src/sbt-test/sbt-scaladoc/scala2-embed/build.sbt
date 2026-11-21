ThisBuild / scalaVersion       := "2.13.18"
ThisBuild / crossScalaVersions := Seq("2.13.18", "2.12.21")

// embeds the scaladoc of its classes, except `demo.model.internal.*`
lazy val model = project
  .enablePlugins(ScaladocPlugin)
  .settings(scaladocExcludeClasses := Seq("demo.model.internal.*"))

// reads it: `model` is on its class path as compiled classes, so `Scaladoc.of` only sees embedded scaladoc
lazy val app = project
  .dependsOn(model)
  .enablePlugins(ScaladocPlugin)
  .settings(scaladocEmbed := false)

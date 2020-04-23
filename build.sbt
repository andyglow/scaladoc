lazy val settingz = Seq(
    version := "0.1",
    scalaVersion := "2.13.1",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1" % Test)
// a scaladox ast
// and parses a string representation of a scaladoc into a scaladoc ast
lazy val core = (project in file("core"))
  .settings(
      settingz,
      name := "scaladoc-parser")

// carries scaladocs alongside with jars in form of attachments or java annotations
lazy val macroCarrier = (project in file("macro-carrier"))
  .dependsOn(core)
  .settings(
      settingz,
      name := "scaladoc-macro-carrier")

lazy val root = (project in file("."))
  .aggregate(core, macroCarrier)
  .settings(
    name := "scaladoc-root",
    crossScalaVersions := Nil,
    publish / skip := true,
    publishArtifact := false,
    aggregate in update := false)
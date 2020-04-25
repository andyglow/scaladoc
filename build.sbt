val scala211 = "2.11.12"
val scala212 = "2.12.10"
val scala213 = "2.13.2"

lazy val commonSettings = Seq(

  version := "0.1",

  scalaVersion := scala213,

  crossScalaVersions := Seq(scala211, scala212, scala213),

  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.1.1" % Test))

// a scaladox ast
// and parses a string representation of a scaladoc into a scaladoc ast
lazy val core = (project in file("core"))
  .settings(
      commonSettings,
      name := "scaladocx")

// carries scaladocs alongside with jars in form of attachments or java annotations
lazy val macroCarrier = (project in file("compiler-plugin"))
  .dependsOn(core)
  .settings(
      commonSettings,
      name := "scaladocx-compiler-pluginr")

lazy val root = (project in file("."))
  .aggregate(core, macroCarrier)
  .settings(
    name := "scaladocx-root",
    crossScalaVersions := Nil,
    publish / skip := true,
    publishArtifact := false,
    aggregate in update := false)
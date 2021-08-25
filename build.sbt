import xerial.sbt.Sonatype._
import ReleaseTransformations._

// https://github.com/xerial/sbt-sonatype/issues/71
ThisBuild / publishTo := sonatypePublishTo.value

ThisBuild / versionScheme := Some("pvp")

val scala211 = "2.11.12"
val scala212 = "2.12.13"
val scala213 = "2.13.5"

lazy val commonSettings = Seq(

  organization := "com.github.andyglow",

  homepage := Some(new URL("http://github.com/andyglow/scaladoc")),

  startYear := Some(2017),

  organizationName := "andyglow",

  scalaVersion := scala212,

  crossScalaVersions := Seq(scala211, scala212, scala213),

  licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))),

  sonatypeProfileName := "com.github.andyglow",

  publishMavenStyle := true,

  sonatypeProjectHosting := Some(
    GitHubHosting(
      "andyglow",
      "scaladoc",
      "andyglow@gmail.com")),

  scmInfo := Some(
    ScmInfo(
      url("https://github.com/andyglow/scaladoc"),
      "scm:git@github.com:andyglow/scaladoc.git")),

  developers := List(
    Developer(
      id    = "andyglow",
      name  = "Andriy Onyshchuk",
      email = "andyglow@gmail.com",
      url   = url("https://ua.linkedin.com/in/andyglow"))),

  releaseCrossBuild := true,

  releasePublishArtifactsAction := PgpKeys.publishSigned.value,

  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
    setNextVersion,
    commitNextVersion,
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true),
    pushChanges),

  javacOptions ++= Seq("-source", "1.8"),

  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.8" % Test),

  Compile / unmanagedSourceDirectories ++= {
    val bd = baseDirectory.value
    def extraDirs(suffix: String): Seq[File] = Seq(bd / "src" / "main" / s"scala$suffix")
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, y)) if y <= 12 => extraDirs("-2.12-")
      case Some((2, y)) if y >= 13 => extraDirs("-2.13+")
      case _                       => Nil
    }
  },

  Test / unmanagedSourceDirectories ++= {
    val bd = baseDirectory.value
    def extraDirs(suffix: String): Seq[File] = Seq(bd / "src" / "test" / s"scala$suffix")
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, y)) if y <= 12 => extraDirs("-2.12-")
      case Some((2, y)) if y >= 13 => extraDirs("-2.13+")
      case _                       => Nil
    }
  }
)

// a scaladoc ast
// and parses a string representation of a scaladoc into a scaladoc ast
lazy val ast = (project in file("ast"))
  .settings(
    commonSettings,
    name := "scaladoc-ast")

lazy val parser = (project in file("parser"))
  .dependsOn(ast % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := "scaladoc-parser",
    scalacOptions += "-language:experimental.macros",
    libraryDependencies += scalaOrganization.value % "scala-reflect" % scalaVersion.value)


// attaches scaladoc to tree
lazy val compilerPlugin = (project in file("compiler-plugin"))
  .dependsOn(ast, parser)
  .settings(
    commonSettings,
    libraryDependencies += scalaOrganization.value % "scala-compiler" % scalaVersion.value,
    name := "scaladoc-compiler-plugin",
    //    Compile / scalacOptions += "-Ydebug",
    //    Test / scalacOptions ++= {
    //      val coreJar = (core / Compile / packageBin).value
    //      val pluginJar = (Compile / packageBin).value
    //      Seq(
    //        s"-Xplugin:${pluginJar.getAbsolutePath}:${coreJar.getAbsolutePath}",
    //        s"-Jdummy=${pluginJar.lastModified}", // ensures recompile
    //         "-Yrangepos")
    //    },
    //    console / initialCommands := "import scaladocx",
    //    Compile / console / scalacOptions := Seq("-language:_", "-Xplugin:" + (Compile / packageBin).value),
    //    Test / console / scalacOptions := (Compile / console / scalacOptions).value,
    //    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test,
    //    testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v"),
    //    Test / fork := true,

    //Test / scalacOptions ++= Seq("-Xprint:typer", "-Xprint-pos"), // Useful for debugging
  )

lazy val itExternal = (project in file("integration-tests/external-models"))
  .settings(
    commonSettings,
    name := "scaladoc-integration-test-external-models",
    Compile / scalacOptions ++= {
      val astJar    = (ast            / Compile / packageBin).value
      val parserJar = (parser         / Compile / packageBin).value
      val pluginJar = (compilerPlugin / Compile / packageBin).value
      Seq(
        s"-Xplugin:${pluginJar.getAbsolutePath}:${parserJar.getAbsolutePath}:${astJar.getAbsolutePath}",
        s"-Jdummy=${pluginJar.lastModified}", // ensures recompile
        // https://github.com/sbt/zinc/issues/621
        // "-Xshow-phases",
        "-Yrangepos")
    },
    Keys.`package` := { new File("") },
    publish / skip := true,
    publishArtifact := false,
    update / aggregate := false)

lazy val it = (project in file("integration-tests/suites"))
  .dependsOn(ast, parser, itExternal)
  .settings(
    commonSettings,
    name := "scaladoc-integration-tests",
    Keys.`package` := { new File("") },
    publish / skip := true,
    publishArtifact := false,
    update / aggregate := false,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.8" % Test)

lazy val root = (project in file("."))
  .aggregate(ast, parser, compilerPlugin, itExternal, it)
  .settings(
    commonSettings,
    name := "scaladoc-root",
    // crossScalaVersions := Nil,
    Keys.`package` := { new File("") },
    publish / skip := true,
    publishArtifact := false,
    update / aggregate := false)
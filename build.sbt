val scala211 = "2.11.12"
val scala212 = "2.12.10"
val scala213 = "2.13.2"

lazy val commonSettings = Seq(

  version := "0.1",

  scalaVersion := scala212,

  crossScalaVersions := Seq(scala211, scala212, scala213),

  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.2" % Test),

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

// a scaladox ast
// and parses a string representation of a scaladoc into a scaladoc ast
lazy val core = (project in file("core"))
  .settings(
    commonSettings,
    name := "scaladocx",
    scalacOptions ++= Seq(
      "-language:experimental.macros"
    ),
    libraryDependencies += scalaOrganization.value % "scala-reflect" % scalaVersion.value)

lazy val itExternal = (project in file("integration-tests/external-models"))
  .settings(
    commonSettings,
    name := "scaladoc-integration-test-external-models",
    Compile / scalacOptions ++= {
      val coreJar = (core / Compile / packageBin).value
      val pluginJar = (embedder / Compile / packageBin).value
      Seq(
        s"-Xplugin:${pluginJar.getAbsolutePath}:${coreJar.getAbsolutePath}",
        s"-Jdummy=${pluginJar.lastModified}", // ensures recompile
        "-Yrangepos")
    },
    publish / skip := true,
    publishArtifact := false,
    aggregate in update := false)

lazy val it = (project in file("integration-tests/suites"))
  .dependsOn(core, itExternal)
  .settings(
    commonSettings,
    name := "scaladoc-integration-tests",
    publish / skip := true,
    publishArtifact := false,
    aggregate in update := false,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1" % Test)

// attaches scaladoc to tree
lazy val embedder = (project in file("embedder"))
  .dependsOn(core)
  .settings(
    commonSettings,
    libraryDependencies += scalaOrganization.value % "scala-compiler" % scalaVersion.value,
    name := "scaladoc-embedder",
    Compile / scalacOptions += "-Ydebug",
    Test / scalacOptions ++= {
      val coreJar = (core / Compile / packageBin).value
      val pluginJar = (Compile / packageBin).value
      Seq(
        s"-Xplugin:${pluginJar.getAbsolutePath}:${coreJar.getAbsolutePath}",
        s"-Jdummy=${pluginJar.lastModified}", // ensures recompile
         "-Yrangepos")
    },
    console / initialCommands := "import scaladocx",
    Compile / console / scalacOptions := Seq("-language:_", "-Xplugin:" + (Compile / packageBin).value),
    Test / console / scalacOptions := (Compile / console / scalacOptions).value,
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test,
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v"),
    Test / fork := true
//Test / scalacOptions ++= Seq("-Xprint:typer", "-Xprint-pos"), // Useful for debugging
)

lazy val root = (project in file("."))
  .aggregate(core, embedder, itExternal, it)
  .settings(
    name := "scaladocx-root",
    // crossScalaVersions := Nil,
    publish / skip := true,
    publishArtifact := false,
    aggregate in update := false)
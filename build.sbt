import ReleaseTransformations._

val scala212 = "2.12.21"
val scala213 = "2.13.18"
val scala3   = "3.3.9"

// `ast` and `parser` cross-build to Scala 3; the compiler plugin (and the integration tests that need it)
// are Scala 2 only. They are still listed for Scala 3 so that `++3.3.9 <task>` keeps a consistent build, but
// on Scala 3 they have no sources and publish nothing (see `scala2Only`).
val allScalaVersions = Seq(scala213, scala212, scala3)

def isScala3(v: String): Boolean = v.startsWith("3.")

// Sonatype Central Portal (sbt 1.11+ built-in): releases are staged locally by `publishSigned`
// and uploaded + released as a single bundle by `sonaRelease`
ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}

ThisBuild / versionScheme      := Some("pvp")
ThisBuild / scalaVersion       := scala212
ThisBuild / crossScalaVersions := allScalaVersions

ThisBuild / organization     := "com.github.andyglow"
ThisBuild / organizationName := "andyglow"
ThisBuild / homepage         := Some(url("https://github.com/andyglow/scaladoc"))
ThisBuild / startYear        := Some(2017)
ThisBuild / licenses         := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / scmInfo          := Some(ScmInfo(url("https://github.com/andyglow/scaladoc"), "scm:git@github.com:andyglow/scaladoc.git"))
ThisBuild / developers := List(
  Developer(id = "andyglow", name = "Andriy Onyshchuk", email = "andyglow@gmail.com", url = url("https://ua.linkedin.com/in/andyglow"))
)

// GitHub Actions (sbt-github-actions): `githubWorkflowGenerate` writes .github/workflows/*.yml
ThisBuild / githubWorkflowPublishTargetBranches := Seq()
ThisBuild / githubWorkflowJavaVersions          := Seq(JavaSpec.temurin("21"))

// Built on JDK 21, but bytecode and JDK API are pinned to Java 8 so the artifacts keep working on older JDKs
def compilerOptions(v: String): Seq[String] = {
  val base = Seq("-encoding", "UTF-8", "-feature", "-unchecked", "-deprecation")
  CrossVersion.partialVersion(v) match {
    case Some((2, 12)) => base ++ Seq("-release:8", "-Ywarn-unused:imports", "-language:higherKinds")
    case Some((2, _))  => base ++ Seq("-release:8", "-Wunused:imports")
    case _             => base ++ Seq("-release", "8", "-Wunused:imports")
  }
}

lazy val commonSettings = Seq(
  scalacOptions := compilerOptions(scalaVersion.value),
  Compile / doc / scalacOptions := scalacOptions.value.filterNot(_.startsWith("-W")).filterNot(_.startsWith("-Ywarn")),
  // `-Xlint:-options`: JDK 21 warns that release 8 is obsolete
  javacOptions ++= Seq("--release", "8", "-Xlint:-options"),
  publishMavenStyle := true,
  releaseCrossBuild             := true,
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
    ReleaseStep(action = Command.process("sonaRelease", _)),
    pushChanges
  ),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test,
  // `scala-2` / `scala-3` (language) dirs are sbt built-ins; these are the collection-API ranges
  Compile / unmanagedSourceDirectories ++= versionRangeDirs(baseDirectory.value / "src" / "main", scalaVersion.value),
  Test / unmanagedSourceDirectories ++= versionRangeDirs(baseDirectory.value / "src" / "test", scalaVersion.value)
)

def versionRangeDirs(base: File, v: String): Seq[File] = CrossVersion.partialVersion(v) match {
  case Some((2, y)) if y <= 12 => Seq(base / "scala-2.12-")
  case _                       => Seq(base / "scala-2.13+")
}

lazy val noPublish = Seq(
  publish / skip  := true,
  publishArtifact := false
)

// Scala 2-only module: on Scala 3 it has no sources, no dependencies and publishes nothing
lazy val scala2Only = Seq(
  Compile / unmanagedSourceDirectories := { if (isScala3(scalaVersion.value)) Nil else (Compile / unmanagedSourceDirectories).value },
  Test / unmanagedSourceDirectories    := { if (isScala3(scalaVersion.value)) Nil else (Test / unmanagedSourceDirectories).value },
  Compile / unmanagedResourceDirectories := {
    if (isScala3(scalaVersion.value)) Nil else (Compile / unmanagedResourceDirectories).value
  },
  publish / skip := { (publish / skip).value || isScala3(scalaVersion.value) }
)

// scaladoc AST
lazy val ast = (project in file("ast"))
  .settings(
    commonSettings,
    name := "scaladoc-ast"
  )

// parses a string representation of a scaladoc into a scaladoc ast.
// Shared code (src/main/scala) is plain Scala over String. Scala 2 adds the `Scaladoc.of[T]` macro, source
// extraction and the `ScaladocCarrier` annotation (src/main/{scala-2,java-2}, needs scala-reflect); Scala 3
// adds `Scaladoc.of[T]` on top of `Symbol.docstring` (src/main/scala-3, no extra dependencies).
lazy val parser = (project in file("parser"))
  .dependsOn(ast % "compile->compile;test->test")
  .settings(
    commonSettings,
    name := "scaladoc-parser",
    libraryDependencies ++= {
      if (isScala3(scalaVersion.value)) Nil
      else Seq(scalaOrganization.value % "scala-reflect" % scalaVersion.value)
    },
    Compile / unmanagedSourceDirectories ++= {
      if (isScala3(scalaVersion.value)) Nil else Seq(baseDirectory.value / "src" / "main" / "java-2")
    }
  )

// attaches scaladoc to tree
lazy val compilerPlugin = (project in file("compiler-plugin"))
  .dependsOn(ast, parser)
  .settings(
    commonSettings,
    scala2Only,
    name := "scaladoc-compiler-plugin",
    libraryDependencies ++= {
      if (isScala3(scalaVersion.value)) Nil
      else Seq(scalaOrganization.value % "scala-compiler" % scalaVersion.value)
    }
  )

lazy val itExternal = (project in file("integration-tests/external-models"))
  .settings(
    commonSettings,
    scala2Only,
    noPublish,
    name := "scaladoc-integration-test-external-models",
    Compile / scalacOptions ++= {
      if (isScala3(scalaVersion.value)) Nil
      else {
        val astJar    = (ast / Compile / packageBin).value
        val parserJar = (parser / Compile / packageBin).value
        val pluginJar = (compilerPlugin / Compile / packageBin).value
        Seq(
          s"-Xplugin:${pluginJar.getAbsolutePath}:${parserJar.getAbsolutePath}:${astJar.getAbsolutePath}",
          s"-Jdummy=${pluginJar.lastModified}", // ensures recompile
          // https://github.com/sbt/zinc/issues/621
          "-Yrangepos"
        )
      }
    }
  )

lazy val it = (project in file("integration-tests/suites"))
  .dependsOn(ast, parser, itExternal)
  .settings(
    commonSettings,
    scala2Only,
    noPublish,
    name := "scaladoc-integration-tests"
  )

lazy val root = (project in file("."))
  .aggregate(ast, parser, compilerPlugin, itExternal, it)
  .settings(
    commonSettings,
    noPublish,
    name := "scaladoc-root"
  )

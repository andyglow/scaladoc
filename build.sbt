import ReleaseTransformations._

val scala212 = "2.12.21"
val scala213 = "2.13.18"
val scala3   = "3.3.9"

// sbt-scaladoc is built for sbt 2 (Scala 3) and sbt 1 (Scala 2.12), against the oldest sbt of each line it supports.
// Scala 3 reads TASTy only from the same or older compilers, so the sbt 2 build uses sbt 2.0.0's Scala.
val sbt2Scala         = "3.8.4"
val sbt2Version       = "2.0.0"
val sbt1Version       = "1.5.8"
// scripted tests run on the latest of each line (older sbt 1 releases don't start on JDK 21)
val scriptedSbt2      = "2.1.0"
val scriptedSbt1      = "1.13.1"

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

ThisBuild / organization     := "io.github.andyglow"
ThisBuild / organizationName := "andyglow"
ThisBuild / homepage         := Some(uri("https://github.com/andyglow/scaladoc"))
ThisBuild / startYear        := Some(2017)
ThisBuild / licenses         := Seq("Apache-2.0" -> uri("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / scmInfo          := Some(ScmInfo(uri("https://github.com/andyglow/scaladoc"), "scm:git@github.com:andyglow/scaladoc.git"))
ThisBuild / developers := List(
  Developer(id = "andyglow", name = "Andriy Onyshchuk", email = "andyglow@gmail.com", url = uri("https://ua.linkedin.com/in/andyglow"))
)

// GitHub Actions (sbt-github-actions): `githubWorkflowGenerate` writes .github/workflows/*.yml
// push builds only for master; feature branches are built by their pull_request run
ThisBuild / githubWorkflowTargetBranches        := Seq("master")
ThisBuild / githubWorkflowPublishTargetBranches := Seq()
ThisBuild / githubWorkflowJavaVersions          := Seq(JavaSpec.temurin("21"))
// Tests on every Scala version; coverage of ast and parser measured once, on 2.13, and uploaded to Codecov
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(List("test"), name = Some("Build project")),
  // sbt-scaladoc against real sbt 2 and sbt 1 builds; they need this build's artifacts in the local repository
  WorkflowStep.Sbt(
    List("+publishLocal", "+sbtScaladoc/scripted"),
    name = Some("sbt plugin scripted tests"),
    cond = Some(s"matrix.scala == '$scala213'")
  ),
  WorkflowStep.Sbt(
    List("coverage", "ast/test", "parser/test", "coverageAggregate"),
    name = Some("Measure coverage"),
    cond = Some(s"matrix.scala == '$scala213'")
  ),
  WorkflowStep.Use(
    UseRef.Public("codecov", "codecov-action", "v7"),
    name = Some("Upload coverage to Codecov"),
    params = Map(
      "token"            -> "${{ secrets.CODECOV_TOKEN }}",
      "files"            -> s"target/out/jvm/scala-$scala213/scaladoc-root/coverage-report/cobertura.xml",
      "fail_ci_if_error" -> "true"
    ),
    cond = Some(s"matrix.scala == '$scala213'")
  )
)

// Built on JDK 21, but bytecode and JDK API are pinned to Java 8 so the artifacts keep working on older JDKs
def compilerOptions(v: String, jvmRelease: Int = 8): Seq[String] = {
  val base = Seq("-encoding", "UTF-8", "-feature", "-unchecked", "-deprecation")
  CrossVersion.partialVersion(v) match {
    case Some((2, 12)) => base ++ Seq(s"-release:$jvmRelease", "-Ywarn-unused:imports", "-language:higherKinds")
    case Some((2, _))  => base ++ Seq(s"-release:$jvmRelease", "-Wunused:imports")
    case _             => base ++ Seq("-release", jvmRelease.toString, "-Wunused:imports")
  }
}

lazy val commonSettings = Seq(
  scalacOptions := compilerOptions(scalaVersion.value),
  Compile / doc / scalacOptions := scalacOptions.value.filterNot(_.startsWith("-W")).filterNot(_.startsWith("-Ywarn")),
  // `-Xlint:-options`: JDK 21 warns that release 8 is obsolete
  javacOptions ++= Seq("--release", "8", "-Xlint:-options"),
  publishMavenStyle := true,
  // sbt-release's cross build puts every project on each Scala version, including sbt-scaladoc, which has no 2.13
  // or 3.3 build; sbt's own `+` keeps each project to its crossScalaVersions, so the steps below use it instead
  releaseCrossBuild             := false,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    releaseStepCommandAndRemaining("+test"),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommandAndRemaining("+publishSigned"),
    // upload while the version is still the release one: the Central Portal rejects SNAPSHOT versions
    releaseStepCommand("sonaRelease"),
    setNextVersion,
    commitNextVersion,
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

// The compiler plugin is loaded into scalac with `-Xplugin`, where instrumented classes can't find the scoverage
// runtime; the integration tests compile with that plugin. Neither is measured.
lazy val noCoverage = Seq(coverageEnabled := false)

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
// scalac loads a compiler plugin in its own class loader with only the `-Xplugin` jars, and sbt's `compilerPlugin(...)`
// passes only jars that contain a scalac-plugin.xml. So the plugin jar is self-contained: it bundles the ast and parser
// classes, and they are not dependencies in its POM ("compile-internal").
lazy val compilerPlugin = (project in file("compiler-plugin"))
  .dependsOn(ast % "compile-internal->compile", parser % "compile-internal->compile")
  .settings(
    commonSettings,
    scala2Only,
    noCoverage,
    name := "scaladoc-compiler-plugin",
    libraryDependencies ++= {
      if (isScala3(scalaVersion.value)) Nil
      else Seq(scalaOrganization.value % "scala-compiler" % scalaVersion.value)
    },
    Compile / packageBin / mappings ++= {
      val bundled = (ast / Compile / packageBin / mappings).value ++ (parser / Compile / packageBin / mappings).value
      if (isScala3(scalaVersion.value)) Nil else bundled
    }
  )

// compiles the module with the scaladoc compiler plugin built from this repo, passing it `pluginOptions`
def withScaladocPlugin(pluginOptions: String*) = Compile / scalacOptions ++= {
  if (isScala3(scalaVersion.value)) Nil
  else {
    val conv      = fileConverter.value
    val pluginJar = (compilerPlugin / Compile / packageBin).value
    // only the plugin jar, as sbt passes it to users' builds: it has to be self-contained
    Seq(
      s"-Xplugin:${conv.toPath(pluginJar).toAbsolutePath}",
      s"-Jdummy=${pluginJar.contentHashStr}", // recompiles when the plugin changes
      // https://github.com/sbt/zinc/issues/621
      "-Yrangepos"
    ) ++ pluginOptions.map(o => s"-P:scaladoc-compiler-plugin:$o")
  }
}

// sbt plugin that adds the compiler plugin (Scala 2) or `-Yread-docs` (Scala 3) and the libraries to a project
lazy val sbtScaladoc = (project in file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    commonSettings,
    noCoverage,
    name               := "sbt-scaladoc",
    scalaVersion       := sbt2Scala,
    crossScalaVersions := Seq(sbt2Scala, scala212),
    // sbt 2 runs on JDK 17+, and Scala 3.8 can't target older JVMs; the sbt 1 build keeps Java 8
    scalacOptions := compilerOptions(scalaVersion.value, jvmRelease = if (isScala3(scalaVersion.value)) 17 else 8),
    pluginCrossBuild / sbtVersion := {
      if (isScala3(scalaVersion.value)) sbt2Version else sbt1Version
    },
    scriptedSbt := {
      if (isScala3(scalaVersion.value)) scriptedSbt2 else scriptedSbt1
    },
    scriptedLaunchOpts ++= Seq("-Xmx1024M", s"-Dplugin.version=${version.value}"),
    scriptedBufferLog  := false,
    // the version of the libraries and the compiler plugin that sbt-scaladoc adds: its own
    Compile / sourceGenerators += Def.task {
      val file = (Compile / sourceManaged).value / "scaladoc" / "sbtplugin" / "BuildInfo.scala"
      IO.write(
        file,
        s"""package scaladoc.sbtplugin
           |
           |private[sbtplugin] object BuildInfo {
           |  val version: String = "${version.value}"
           |}
           |""".stripMargin)
      Seq(file)
    }
  )

lazy val itExternal = (project in file("integration-tests/external-models"))
  .settings(
    commonSettings,
    scala2Only,
    noCoverage,
    noPublish,
    name := "scaladoc-integration-test-external-models",
    withScaladocPlugin()
  )

// models compiled with include/exclude options; see FilteredClassesSpec for what is expected to be embedded
lazy val itFiltered = (project in file("integration-tests/filtered-models"))
  .settings(
    commonSettings,
    scala2Only,
    noCoverage,
    noPublish,
    name := "scaladoc-integration-test-filtered-models",
    withScaladocPlugin(
      "include:path:**/filtered/model/**",
      "include:class:scaladoc.test.filtered.extra.Kept*",
      "include:class:scaladoc.test.filtered.extra.Box.Inner",
      "exclude:path:**/Skipped.scala",
      "exclude:class:scaladoc.test.filtered.**.internal.*"
    )
  )

lazy val it = (project in file("integration-tests/suites"))
  .dependsOn(ast, parser, itExternal, itFiltered)
  .settings(
    commonSettings,
    scala2Only,
    noCoverage,
    noPublish,
    name := "scaladoc-integration-tests"
  )

lazy val root = (project in file("."))
  .aggregate(ast, parser, compilerPlugin, sbtScaladoc, itExternal, itFiltered, it)
  .settings(
    commonSettings,
    noPublish,
    name := "scaladoc-root"
  )

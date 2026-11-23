import ReleaseTransformations._

val scala212 = "2.12.21"
val scala213 = "2.13.18"
val scala3   = "3.9.0"

// sbt-scaladoc is built for sbt 2 (Scala 3) and sbt 1 (Scala 2.12), against the oldest sbt of each line it supports.
// Scala 3 reads TASTy only from the same or older compilers, so the sbt 2 build uses sbt 2.0.0's Scala.
// Bumping it breaks the plugin on every sbt 2 that runs an older Scala (sbt 2.1.0 runs 3.9.0, which can't read 3.10
// TASTy), so Scala Steward is told to leave it alone.
val sbt2Scala         = "3.8.4" // scala-steward:off
val sbt2Version       = "2.0.0"
val sbt1Version       = "1.5.8"
// scripted tests run on the latest of each line (older sbt 1 releases don't start on JDK 21)
val scriptedSbt2      = "2.1.0"
val scriptedSbt1      = "1.13.1"

// `core` and `parser` cross-build to Scala 3; the compiler plugin (and the integration tests that need it)
// are Scala 2 only. They are still listed for Scala 3 so that `++3.9.0 <task>` keeps a consistent build, but
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
// Tests on every Scala version; coverage of core and parser measured once, on 2.13, and uploaded to Codecov
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(List("test"), name = Some("Build project")),
  // sbt-scaladoc against real sbt 2 and sbt 1 builds; they need this build's artifacts in the local repository
  WorkflowStep.Sbt(
    List("+publishLocal", "+sbtScaladoc/scripted"),
    name = Some("sbt plugin scripted tests"),
    cond = Some(s"matrix.scala == '$scala213'")
  ),
  // the Gradle plugin, and the Maven setup the docs show, against real Gradle and Maven builds; they resolve this
  // build's libraries from the local Maven repository
  WorkflowStep.Sbt(
    List("+publishM2", "gradlePluginCheck", "mavenCheck"),
    name = Some("Gradle plugin and Maven tests"),
    cond = Some(s"matrix.scala == '$scala213'")
  ),
  // the site: mdoc compiles and runs its snippets, Paradox checks its links
  WorkflowStep.Sbt(List("docs/makeSite"), name = Some("Build the documentation site"), cond = Some(s"matrix.scala == '$scala213'")),
  WorkflowStep.Sbt(
    List("coverage", "coreJVM/test", "parserJVM/test", "coverageAggregate"),
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

// Built on JDK 21, but bytecode and JDK API are pinned to the oldest JDK each Scala supports: Java 8 for Scala 2, so
// the artifacts keep working on older JDKs, and Java 17 for Scala 3, which can't target older JVMs from 3.8 on
def compilerOptions(v: String): Seq[String] = {
  val jvmRelease = if (isScala3(v)) 17 else 8
  val base = Seq("-encoding", "UTF-8", "-feature", "-unchecked", "-deprecation")
  CrossVersion.partialVersion(v) match {
    case Some((2, 12)) => base ++ Seq(s"-release:$jvmRelease", "-Ywarn-unused:imports", "-language:higherKinds")
    case Some((2, _))  => base ++ Seq(s"-release:$jvmRelease", "-Wunused:imports")
    case _             => base ++ Seq("-release", jvmRelease.toString, "-Wunused:imports")
  }
}

lazy val commonSettings = Seq(
  scalacOptions ++= compilerOptions(scalaVersion.value), // `++=`: keeps `-scalajs`, which Scala 3 needs to emit Scala.js IR
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
    // the Gradle plugin goes out with the libraries it adds, in the same version
    releaseStepTask(gradlePluginPublish),
    setNextVersion,
    commitNextVersion,
    pushChanges
  ),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test,
  // `scala-2` / `scala-3` (language) dirs are sbt built-ins; these are the collection-API ranges
  Compile / unmanagedSourceDirectories ++= versionRangeDirs(sourceBase(baseDirectory.value) / "src" / "main", scalaVersion.value),
  Test / unmanagedSourceDirectories ++= versionRangeDirs(sourceBase(baseDirectory.value) / "src" / "test", scalaVersion.value)
)

// a cross (JVM + Scala.js) project of `CrossType.Pure` lives in `<module>/.jvm` and `<module>/.js`, with its sources in
// `<module>/src`
def sourceBase(base: File): File = if (base.getName.startsWith(".")) base.getParentFile else base

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

// the scaladoc model: `Scaladoc`, its tags and markup, and the helpers that read them
lazy val core = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/libs/core"))
  .settings(
    commonSettings,
    name := "scaladoc-core"
  )
  .jsSettings(jsSettings)

lazy val coreJVM = core.jvm
lazy val coreJS  = core.js

// Scala.js: coverage is measured on the JVM only
lazy val jsSettings = Seq(
  coverageEnabled := false
)

// The `ScaladocCarrier` annotation, which carries a class's scaladoc in its bytecode: plain Java, with no Scala suffix
// and no dependencies, so anything can read embedded scaladoc with just this jar. One jar serves every Scala version:
// it's built in each round of `+`, so the projects that depend on it can, and published in the 2.12 one only.
lazy val annotation = (project in file("modules/libs/annotation"))
  .settings(
    commonSettings,
    noCoverage,
    name             := "scaladoc-annotation",
    crossPaths       := false,
    autoScalaLibrary := false,
    publish / skip   := scalaBinaryVersion.value != "2.12",
    // no dependencies at all, not even the shared settings' test ones
    libraryDependencies := Nil,
    // javadoc knows --release but not javac's -Xlint
    Compile / doc / javacOptions := (Compile / doc / javacOptions).value.filterNot(_.startsWith("-Xlint"))
  )

// parses a string representation of a scaladoc into the scaladoc model (core).
// Shared code (src/main/scala) is plain Scala over String. Scala 2 adds the `Scaladoc.of[T]` macro, which also reads
// the `ScaladocCarrier` annotation, and source extraction (src/main/scala-2, needs scala-reflect); Scala 3 adds
// `Scaladoc.of[T]` on top of `Symbol.docstring` (src/main/scala-3, no extra dependencies).
lazy val parser = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/libs/parser"))
  .dependsOn(core % "compile->compile;test->test")
  .jvmConfigure(_.dependsOn(annotation))
  .jsConfigure(_.dependsOn(annotation))
  .settings(
    commonSettings,
    name := "scaladoc-parser",
    libraryDependencies ++= {
      if (isScala3(scalaVersion.value)) Nil
      else Seq(scalaOrganization.value % "scala-reflect" % scalaVersion.value)
    },
    // only the Scala 2 macro reads the annotation, so the Scala 3 build doesn't depend on it
    projectDependencies := {
      val deps = projectDependencies.value
      if (isScala3(scalaVersion.value)) deps.filterNot(_.name == "scaladoc-annotation") else deps
    }
  )
  .jsSettings(
    jsSettings,
    // JVM-only tests: FilesSuite reads its cases from files, SourceCodeUtilsSpec runs scalac's position API
    Test / unmanagedSources / excludeFilter := {
      (Test / unmanagedSources / excludeFilter).value || "FilesSuite.scala" || "SourceCodeUtilsSpec.scala" || "CommonMarkSpecSuite.scala" || "MarkdownPathologicalSuite.scala"
    }
  )

lazy val parserJVM = parser.jvm
lazy val parserJS  = parser.js

// attaches scaladoc to tree
// scalac loads a compiler plugin in its own class loader with only the `-Xplugin` jars, and sbt's `compilerPlugin(...)`
// passes only jars that contain a scalac-plugin.xml. So the plugin jar is self-contained: it bundles the annotation,
// core and parser classes, and they are not dependencies in its POM ("compile-internal"). That's also why a module
// whose scaladoc gets embedded needs nothing besides the plugin.
lazy val compilerPlugin = (project in file("modules/plugins/scalac"))
  .dependsOn(
    annotation % "compile-internal->compile",
    coreJVM    % "compile-internal->compile",
    parserJVM  % "compile-internal->compile"
  )
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
      val bundled = (annotation / Compile / packageBin / mappings).value ++
        (coreJVM / Compile / packageBin / mappings).value ++ (parserJVM / Compile / packageBin / mappings).value
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
lazy val sbtScaladoc = (project in file("modules/plugins/sbt"))
  .enablePlugins(SbtPlugin)
  .settings(
    commonSettings,
    noCoverage,
    name               := "sbt-scaladoc",
    scalaVersion       := sbt2Scala,
    crossScalaVersions := Seq(sbt2Scala, scala212),
    scalacOptions := compilerOptions(scalaVersion.value),
    pluginCrossBuild / sbtVersion := {
      if (isScala3(scalaVersion.value)) sbt2Version else sbt1Version
    },
    scriptedSbt := {
      if (isScala3(scalaVersion.value)) scriptedSbt2 else scriptedSbt1
    },
    scriptedLaunchOpts ++= Seq("-Xmx1024M", s"-Dplugin.version=${version.value}", s"-Dscalajs.version=$scalaJSVersion"),
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

// the integration tests run on the JVM and on Scala.js: on Scala.js the models are compiled with the compiler plugin
// next to the Scala.js one, and `Scaladoc.of` reads them from the compiled classes in the same way
lazy val itExternal = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/tests/integration/external-models"))
  .settings(
    commonSettings,
    scala2Only,
    noCoverage,
    noPublish,
    name := "scaladoc-integration-test-external-models",
    withScaladocPlugin()
  )

// models compiled with include/exclude options; see FilteredClassesSpec for what is expected to be embedded
lazy val itFiltered = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/tests/integration/filtered-models"))
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

lazy val it = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/tests/integration/suites"))
  .dependsOn(core, parser, itExternal, itFiltered)
  .settings(
    commonSettings,
    scala2Only,
    noCoverage,
    noPublish,
    name := "scaladoc-integration-tests"
  )

// The documentation site, https://andyglow.github.io/scaladoc: mdoc checks the Scala snippets in docs/src against
// the libraries, Paradox renders the pages with the Material theme (search included), and `docs/ghpagesPushSite`
// publishes them to the gh-pages branch. Not in the root aggregate, so it's never cross built or published.
// `version` itself when it's a release, the newest `v*` tag when it's a snapshot (or `version` without a tag)
def releasedVersion(version: String, dir: File): String =
  if (!version.endsWith("-SNAPSHOT")) version
  else
    scala.util.Try(scala.sys.process.Process(Seq("git", "describe", "--tags", "--abbrev=0", "--match", "v*"), dir).!!.trim)
      .toOption.filter(_.nonEmpty).map(_.stripPrefix("v")).getOrElse(version)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(MdocPlugin, ParadoxMaterialThemePlugin, SitePreviewPlugin, GhpagesPlugin)
  .dependsOn(coreJVM, parserJVM)
  .settings(
    noPublish,
    name               := "scaladoc-docs",
    scalaVersion       := scala213,
    crossScalaVersions := Seq(scala213),
    mdocIn             := baseDirectory.value / "src",
    mdocOut            := target.value / "mdoc",
    // install snippets show the latest release: this version when building one, the newest v* tag otherwise
    mdocVariables      := Map("VERSION" -> releasedVersion(version.value, baseDirectory.value)),
    Compile / paradox / sourceDirectory := mdocOut.value,
    // without it mdoc keeps the output of pages that are gone, which Paradox rejects as pages missing from the ToC
    mdocExtraArguments := Seq("--clean-target"),
    // mdoc runs as a `runMain`. Called from sbt 2's client (plain `sbt`, as on CI) that's a client-side run, which
    // the task doesn't wait for, so Paradox would look for the pages before mdoc wrote them
    Compile / run / clientSide     := false,
    Compile / runMain / clientSide := false,
    // Paradox renders what mdoc writes, so it lists its pages once mdoc is done. Uncached: sbt's cache only knows the
    // directory, not what mdoc put in it, and an empty listing restored from the CI cache would stick
    Compile / paradoxMarkdownToHtml / mappings := Def.uncached {
      (Compile / paradoxMarkdownToHtml / mappings).dependsOn(mdoc.toTask("")).value
    },
    Compile / paradoxProperties ++= Map(
      "project.name"    -> "Scaladoc",
      "github.base_url" -> "https://github.com/andyglow/scaladoc/tree/master"
    ),
    Compile / paradoxMaterialTheme ~= {
      _.withColor("light-blue", "cyan")
        .withLogoIcon("description")
        .withRepository(uri("https://github.com/andyglow/scaladoc"))
        .withCopyright("Apache-2.0 · andyglow")
    },
    siteSourceDirectory := (Compile / paradox / target).value,
    // sbt-site's default takes html, css, images and js; the theme also needs its search index and its fonts
    makeSite / includeFilter := (makeSite / includeFilter).value || "*.json" || "*.ttf" || "*.woff" || "*.woff2" || "*.svg",
    // the site is what Paradox wrote, so it's listed once Paradox is done. Uncached, like Paradox's own page list:
    // sbt's cache knows the directory, not what's in it. `ghpagesPushSite` takes this list without running makeSite,
    // so without the dependency a fresh build (CI's) pushed an empty site; the check keeps that from happening again.
    makeSite / mappings := Def.uncached {
      val site = (makeSite / mappings).dependsOn(Compile / paradox).value
      if (!site.exists(_._2 == "index.html")) sys.error(s"docs: the site has no index.html (${site.size} files)")
      site
    },
    ghpagesNoJekyll     := true,
    // CI pushes with its token, a developer with their SSH key
    git.remoteRepo := sys.env.get("GITHUB_TOKEN").fold("git@github.com:andyglow/scaladoc.git") { token =>
      s"https://x-access-token:$token@github.com/andyglow/scaladoc.git"
    }
  )

// The Gradle plugin, in modules/plugins/gradle, is a Gradle build of its own. These tasks run it with this build's version,
// so CI tests it (after `+publishM2`, as its test builds take the libraries from the local Maven repository) and the
// release publishes it with everything else.
lazy val gradlePluginCheck   = taskKey[Unit]("Builds and tests the Gradle plugin; needs `+publishM2` first")
lazy val gradlePluginPublish = taskKey[Unit]("Publishes the Gradle plugin to the Gradle Plugin Portal")

// modules/tests/maven is the Maven setup that the docs show, as a real build; it runs once per Scala version, from clean, so
// one version's classes are never read by another
lazy val mavenCheck = taskKey[Unit]("Tests the documented Maven setup on each Scala version; needs `+publishM2` first")

def mvnw(base: File, version: String, log: Logger, scalaBinary: String): Unit = {
  val command = Seq("./mvnw", "-B", s"-Dscala=$scalaBinary", s"-Dscaladoc.version=$version", "clean", "verify")
  log.info(s"maven-tests: ${command.mkString(" ")}")
  val exit = scala.sys.process.Process(command, base / "modules" / "tests" / "maven").!(
    scala.sys.process.ProcessLogger(line => log.info(line), line => log.warn(line)))
  if (exit != 0) sys.error(s"maven-tests: Scala $scalaBinary failed with exit code $exit")
}

def gradlew(base: File, version: String, log: Logger, tasks: String*): Unit = {
  val command = Seq("./gradlew", s"-PscaladocVersion=$version") ++ tasks
  log.info(s"gradle-plugin: ${command.mkString(" ")}")
  val exit = scala.sys.process.Process(command, base / "modules" / "plugins" / "gradle").!(
    scala.sys.process.ProcessLogger(line => log.info(line), line => log.warn(line)))
  if (exit != 0) sys.error(s"gradle-plugin: ${tasks.mkString(" ")} failed with exit code $exit")
}

lazy val root = (project in file("."))
  .aggregate(
    annotation, coreJVM, coreJS, parserJVM, parserJS, compilerPlugin, sbtScaladoc,
    itExternal.jvm, itExternal.js, itFiltered.jvm, itFiltered.js, it.jvm, it.js
  )
  .settings(
    commonSettings,
    noPublish,
    name := "scaladoc-root",
    // uncached: they run Gradle, whose inputs sbt doesn't see
    gradlePluginCheck   := Def.uncached(gradlew(baseDirectory.value, version.value, streams.value.log, "check")),
    gradlePluginPublish := Def.uncached(gradlew(baseDirectory.value, version.value, streams.value.log, "publishPlugins")),
    mavenCheck := Def.uncached {
      Seq("2.13", "2.12", "3").foreach(mvnw(baseDirectory.value, version.value, streams.value.log, _))
    }
  )

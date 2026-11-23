// The Gradle plugin, io.github.andyglow.scaladoc: the Gradle counterpart of sbt-scaladoc. A Gradle build of its own,
// so it gets Gradle's plugin tooling (plugin markers, TestKit, the Plugin Portal); the sbt build drives it, see the
// `gradlePlugin*` tasks in ../build.sbt.

plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.2.1"
}

group = "io.github.andyglow"

// One version for the whole repository: ../version.sbt's, unless the sbt build passes it in with -PscaladocVersion
version = providers.gradleProperty("scaladocVersion").getOrElse(
    Regex("""version\s*:=\s*"([^"]+)"""")
        .find(rootDir.resolve("../version.sbt").readText())
        ?.groupValues?.get(1)
        ?: error("no version in ../version.sbt")
)

repositories {
    mavenCentral()
}

// Java 8 bytecode, so the plugin runs wherever Gradle 8 does; the tests are on 17, which JUnit 6 needs
tasks.compileJava { options.release = 8 }
tasks.compileTestJava { options.release = 17 }

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    website = "https://andyglow.github.io/scaladoc"
    vcsUrl = "https://github.com/andyglow/scaladoc"
    plugins {
        create("scaladoc") {
            id = "io.github.andyglow.scaladoc"
            implementationClass = "scaladoc.gradle.ScaladocPlugin"
            displayName = "Scaladoc"
            description = "Lets Scala code read the doc comments of its own classes, as data, at compile time: for " +
                "example to give a derived JSON Schema or OpenAPI spec the titles and descriptions already written " +
                "in the scaladoc. It adds the scaladoc libraries to Scala 2 and Scala 3 modules, and on Scala 2 " +
                "keeps the comments in the compiled classes, so they can be read from other modules and jars."
            tags = listOf("scala", "scaladoc", "documentation")
        }
    }
}

// the plugin knows its own version, which is the default version of the libraries it adds
tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("scaladoc-gradle-plugin.properties") {
        expand("version" to version)
    }
}

tasks.test {
    useJUnitPlatform()
    // the test builds resolve this version of the libraries from the local Maven repository, so run
    // `sbt +publishM2` first (the sbt build's `gradlePluginCheck` does it all)
    systemProperty("scaladoc.version", project.version.toString())
    // the libraries they take from the local Maven repository aren't inputs Gradle sees, so never skip the tests
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

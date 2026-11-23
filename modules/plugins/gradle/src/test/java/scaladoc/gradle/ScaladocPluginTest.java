package scaladoc.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Real Gradle builds of two modules, as in sbt-scaladoc's scripted tests: {@code model} has documented classes, and
 * {@code app} reads their scaladoc with {@code Scaladoc.of} and fails if it's not what was written. On Scala 2, the
 * compiler plugin embeds it into {@code model}'s classes, except {@code demo.model.internal.*}; on Scala 3,
 * {@code app} reads it from TASTy.
 *
 * <p>The libraries come from the local Maven repository in the version under test, so {@code sbt +publishM2} runs
 * first.
 */
class ScaladocPluginTest {

    private static final String VERSION = System.getProperty("scaladoc.version");

    // the oldest Gradle the plugin is tested with; it runs on Java 21, like CI
    private static final String OLDEST_GRADLE = "8.5";

    @TempDir
    Path dir;

    @ParameterizedTest
    @ValueSource(strings = {"2.13.18", "2.12.21"})
    void scala2EmbedsScaladocIntoBytecode(String scala) {
        fixture(scala2(scala), true);
        BuildResult result = runner(":app:run", "--args=excluded").build();
        assertTrue(result.getOutput().contains("scaladoc: ok"), result.getOutput());
    }

    @Test
    void scala3ReadsScaladocFromTasty() {
        fixture("implementation 'org.scala-lang:scala3-library_3:3.3.9'", true);
        BuildResult result = runner(":app:run").build();
        assertTrue(result.getOutput().contains("scaladoc: ok"), result.getOutput());
    }

    @Test
    void worksOnTheOldestGradle() {
        fixture(scala2("2.13.18"), true);
        BuildResult result = runner(":app:run", "--args=excluded").withGradleVersion(OLDEST_GRADLE).build();
        assertTrue(result.getOutput().contains("scaladoc: ok"), result.getOutput());
    }

    @Test
    void addsWhatEachModuleNeeds() {
        fixture(scala2("2.13.18"), false);
        // the model publishes: the compiler plugin, and none of the libraries, not even to compile
        String model = runner(":model:dependencies", "--configuration", "scalaCompilerPlugins").build().getOutput();
        assertTrue(model.contains("io.github.andyglow:scaladoc-compiler-plugin_2.13:" + VERSION), model);
        String modelCompile = runner(":model:dependencies", "--configuration", "compileClasspath").build().getOutput();
        assertTrue(!modelCompile.contains("io.github.andyglow"), modelCompile);
        // the app reads scaladoc: the parser, which it depends on itself, at runtime
        String app = runner(":app:dependencies", "--configuration", "runtimeClasspath").build().getOutput();
        assertTrue(app.contains("io.github.andyglow:scaladoc-parser_2.13:" + VERSION), app);
    }

    @Test
    void deprecatedEmbedStillLeavesTheCompilerPluginOutAndWarns() {
        write("settings.gradle", "rootProject.name = 'fixture'\n");
        write("build.gradle", String.join("\n",
            "plugins {",
            "  id 'scala'",
            "  id 'io.github.andyglow.scaladoc'",
            "}",
            "repositories { mavenLocal(); mavenCentral() }",
            "dependencies { " + scala2("2.13.18") + " }",
            "scaladoc { embed.set(false) }",
            ""));
        String output = runner("dependencies", "--configuration", "scalaCompilerPlugins").build().getOutput();
        assertTrue(output.contains("`embed` is deprecated"), output);
        assertTrue(!output.contains("scaladoc-compiler-plugin"), output);
    }

    @Test
    void deprecatedReadStillAddsTheLibrariesAndWarns() {
        write("settings.gradle", "rootProject.name = 'fixture'\n");
        write("build.gradle", String.join("\n",
            "plugins {",
            "  id 'scala'",
            "  id 'io.github.andyglow.scaladoc'",
            "}",
            "repositories { mavenLocal(); mavenCentral() }",
            "dependencies { " + scala2("2.13.18") + " }",
            "scaladoc { read.set(true) }",
            ""));
        String output = runner("dependencies", "--configuration", "runtimeClasspath").build().getOutput();
        assertTrue(output.contains("`read` is deprecated"), output);
        assertTrue(output.contains("io.github.andyglow:scaladoc-parser_2.13:" + VERSION), output);
    }

    @Test
    void scala3PublisherGetsNothing() {
        fixture("implementation 'org.scala-lang:scala3-library_3:3.3.9'", false);
        String model = runner(":model:dependencies", "--configuration", "compileClasspath").build().getOutput();
        assertTrue(!model.contains("io.github.andyglow"), model);
    }

    @Test
    void warnsWithoutAScalaLibrary() {
        write("settings.gradle", "rootProject.name = 'fixture'\n");
        write("build.gradle", "plugins {\n  id 'scala'\n  id 'io.github.andyglow.scaladoc'\n}\n");
        BuildResult result = runner("help").build();
        assertTrue(result.getOutput().contains("scaladoc: no scala-library or scala3-library_3 dependency"),
            result.getOutput());
    }

    private static String binary(String scalaLibrary) {
        if (scalaLibrary.contains("scala3-library_3")) return "3";
        String version = scalaLibrary.replaceAll(".*:(\\d+\\.\\d+)\\.\\d+'.*", "$1");
        return version;
    }

    private static String scala2(String version) {
        return "implementation 'org.scala-lang:scala-library:" + version + "'";
    }

    // the two modules, with `scalaLibrary` as their Scala dependency; `sources` copies the Scala sources too
    private void fixture(String scalaLibrary, boolean sources) {
        write("settings.gradle", "rootProject.name = 'fixture'\ninclude 'model', 'app'\n");
        write("model/build.gradle", String.join("\n",
            "plugins {",
            "  id 'scala'",
            "  id 'io.github.andyglow.scaladoc'",
            "}",
            "repositories { mavenLocal(); mavenCentral() }",
            "dependencies { " + scalaLibrary + " }",
            "scaladoc { excludeClasses.add('demo.model.internal.*') }",
            ""));
        write("app/build.gradle", String.join("\n",
            "plugins {",
            "  id 'scala'",
            "  id 'application'",
            "  id 'io.github.andyglow.scaladoc'",
            "}",
            "repositories { mavenLocal(); mavenCentral() }",
            "dependencies {",
            "  " + scalaLibrary,
            "  implementation project(':model')",
            "  // reads scaladoc: depends on the parser like on any library it calls",
            "  implementation 'io.github.andyglow:scaladoc-parser_" + binary(scalaLibrary) + ":" + VERSION + "'",
            "}",
            "application { mainClass.set('demo.app.Main') }",
            ""));
        if (sources) copySources();
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
            .forwardOutput();
    }

    private void write(String path, String content) {
        try {
            Path file = dir.resolve(path);
            Files.createDirectories(file.getParent());
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void copySources() {
        try {
            Path from = Paths.get(getClass().getResource("/fixture").toURI());
            try (Stream<Path> files = Files.walk(from)) {
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    Path to = dir.resolve(from.relativize(file).toString());
                    Files.createDirectories(to.getParent());
                    Files.copy(file, to);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}

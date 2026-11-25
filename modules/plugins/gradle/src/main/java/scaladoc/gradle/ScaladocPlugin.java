package scaladoc.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.scala.ScalaCompile;

/**
 * Sets the Scala compiler up for scaladoc, as sbt-scaladoc does for sbt. Apply it next to Gradle's {@code scala} plugin:
 *
 * <ul>
 *   <li><b>Scala 2:</b> the compiler plugin, which embeds the scaladoc of the module's classes into their bytecode;
 *       the {@code scaladoc} block's include and exclude patterns become its options. The module depends on
 *       {@code scaladoc-annotation}, the one-class jar of the annotation that holds it, and exposes it to its
 *       consumers: its classes refer to it, and Scala 3 modules that use them don't compile without it.</li>
 *   <li><b>Scala 3:</b> {@code -Xread-docs} ({@code -Yread-docs} before Scala 3.5), which lets {@code Scaladoc.of}
 *       read doc comments from other modules and jars. Scala 3 keeps doc comments in TASTy on its own, so nothing else
 *       is needed.</li>
 * </ul>
 *
 * It adds no library for reading: a module that reads scaladoc with {@code Scaladoc.of} depends on
 * {@code scaladoc-parser} itself.
 * The Scala version is the one of the module's {@code scala3-library_3} or {@code scala-library} dependency.
 */
public class ScaladocPlugin implements Plugin<Project> {

    static final String GROUP = "io.github.andyglow";

    static final String COMPILER_PLUGIN = "scaladoc-compiler-plugin";

    static final String ANNOTATION = "scaladoc-annotation";

    // holds scaladoc-annotation; `implementation` and `apiElements` extend it, so the module compiles and runs with it,
    // and it's a compile dependency of whatever uses the module, published POMs included, with or without java-library
    static final String ANNOTATION_CONFIGURATION = "scaladocAnnotation";

    // the configurations a build declares its Scala library in
    private static final List<String> DECLARING =
        Arrays.asList("api", "implementation", "compileOnly", "compileOnlyApi");

    @Override
    public void apply(Project project) {
        ScaladocExtension extension = project.getExtensions().create("scaladoc", ScaladocExtension.class);
        extension.getVersion().convention(ownVersion());
        @SuppressWarnings("deprecation")
        Property<Boolean> read = extension.getRead();
        read.convention(false);
        extension.getIncludePaths().convention(Collections.emptyList());
        extension.getExcludePaths().convention(Collections.emptyList());
        extension.getIncludeClasses().convention(Collections.emptyList());
        extension.getExcludeClasses().convention(Collections.emptyList());

        // the Scala version comes from the dependencies, which the build script declares after applying the plugin
        project.getPluginManager().withPlugin("scala", scala -> project.afterEvaluate(p -> configure(p, extension)));
    }

    private static void configure(Project project, ScaladocExtension extension) {
        String scalaVersion = scalaVersion(project);
        String scala = scalaVersion == null ? null : binaryVersion(scalaVersion);
        if (scala == null) {
            project.getLogger().warn(
                "scaladoc: no scala-library or scala3-library_3 dependency in {}, so the plugin added nothing",
                project.getPath());
            return;
        }

        String version = extension.getVersion().get();
        DependencyHandler dependencies = project.getDependencies();
        String core = GROUP + ":scaladoc-core_" + scala + ":" + version;
        String parser = GROUP + ":scaladoc-parser_" + scala + ":" + version;

        @SuppressWarnings("deprecation")
        Boolean embedSetting = extension.getEmbed().getOrNull();
        if (embedSetting != null) {
            // deprecated, until 0.6.0
            project.getLogger().warn(
                "scaladoc: `embed` is deprecated and will be removed in 0.6.0; a module that shouldn't embed scaladoc "
                    + "doesn't apply the plugin");
        }
        boolean embed = embedSetting == null || embedSetting;

        if (scala.equals("3")) {
            addCompilerOptions(project, Collections.singletonList(readDocsOption(scalaVersion)));
        } else if (embed) {
            // the compiler plugin's jar has everything it needs to run
            dependencies.add("scalaCompilerPlugins", GROUP + ":" + COMPILER_PLUGIN + "_" + scala + ":" + version);
            addCompilerOptions(project, compilerPluginOptions(extension));
            // the classes it embeds scaladoc into refer to the annotation in their Scala signatures
            exposeAnnotation(project);
            dependencies.add(ANNOTATION_CONFIGURATION, GROUP + ":" + ANNOTATION + ":" + version);
        }
        @SuppressWarnings("deprecation")
        boolean read = extension.getRead().get();
        if (read) {
            // deprecated, until 0.6.0
            project.getLogger().warn(
                "scaladoc: `read` is deprecated and will be removed in 0.6.0; depend on scaladoc-parser instead: "
                    + "implementation(\"{}\")", parser);
            dependencies.add("implementation", core);
            dependencies.add("implementation", parser);
        }
    }

    private static void exposeAnnotation(Project project) {
        Configuration annotation = project.getConfigurations().create(ANNOTATION_CONFIGURATION, c -> {
            c.setDescription("scaladoc-annotation, which the classes the scaladoc compiler plugin embeds into refer to");
            c.setCanBeConsumed(false);
            c.setCanBeResolved(false);
            c.setVisible(false);
        });
        project.getConfigurations().getByName("implementation").extendsFrom(annotation);
        project.getConfigurations().getByName("apiElements").extendsFrom(annotation);
    }

    /** The compiler plugin's {@code -P:} options for the extension's include and exclude patterns. */
    static List<String> compilerPluginOptions(ScaladocExtension extension) {
        List<String> options = new ArrayList<>();
        addOptions(options, "include:path", extension.getIncludePaths().get());
        addOptions(options, "exclude:path", extension.getExcludePaths().get());
        addOptions(options, "include:class", extension.getIncludeClasses().get());
        addOptions(options, "exclude:class", extension.getExcludeClasses().get());
        return options;
    }

    private static void addOptions(List<String> options, String kind, List<String> patterns) {
        for (String pattern : patterns) options.add("-P:" + COMPILER_PLUGIN + ":" + kind + ":" + pattern);
    }

    private static void addCompilerOptions(Project project, List<String> options) {
        if (options.isEmpty()) return;
        project.getTasks().withType(ScalaCompile.class).configureEach(task -> {
            List<String> current = task.getScalaCompileOptions().getAdditionalParameters();
            List<String> all = new ArrayList<>(current == null ? Collections.<String>emptyList() : current);
            all.addAll(options);
            task.getScalaCompileOptions().setAdditionalParameters(all);
        });
    }

    /**
     * The version of the declared Scala library: {@code scala3-library_3}'s, or {@code scala-library}'s, which is Scala
     * 3's too from 3.8 on. {@code 3} for a {@code scala3-library_3} without a plain one: none, from a platform for
     * instance, or a dynamic one, like {@code latest.release}. {@code null} if there's no Scala library.
     */
    static String scalaVersion(Project project) {
        String found = null;
        for (String name : DECLARING) {
            Configuration configuration = project.getConfigurations().findByName(name);
            if (configuration == null) continue;
            for (Dependency dependency : configuration.getDependencies()) {
                if (!"org.scala-lang".equals(dependency.getGroup())) continue;
                String version = dependency.getVersion();
                if ("scala3-library_3".equals(dependency.getName()))
                    return version != null && version.startsWith("3.") ? version : "3";
                if ("scala-library".equals(dependency.getName()) && version != null) found = version;
            }
        }
        return found;
    }

    /** {@code 3}, {@code 2.13} or {@code 2.12}, for a Scala version; {@code null} for one that isn't. */
    static String binaryVersion(String scalaVersion) {
        int major = number(scalaVersion, 0);
        if (major == 3) return "3";
        int minor = minor(scalaVersion);
        return major == 2 && minor >= 0 ? "2." + minor : null;
    }

    /**
     * The option that makes Scala 3 read doc comments from TASTy: {@code -Xread-docs}, or {@code -Yread-docs} before
     * Scala 3.5, which named it so. Each version takes only its own name without a warning, and the older ones ignore
     * the newer name, a warning that fails builds with {@code -Werror}. A version without a minor is taken as a recent
     * one.
     */
    static String readDocsOption(String scalaVersion) {
        int minor = minor(scalaVersion);
        return minor >= 0 && minor < 5 ? "-Yread-docs" : "-Xread-docs";
    }

    // the number after the first dot; -1 if there's none
    private static int minor(String version) {
        int dot = version.indexOf('.');
        return dot < 0 ? -1 : number(version, dot + 1);
    }

    // the number that starts at `from`, up to the next non-digit; -1 if there's none
    private static int number(String text, int from) {
        int to = from;
        while (to < text.length() && Character.isDigit(text.charAt(to))) to++;
        return to == from || to - from > 9 ? -1 : Integer.parseInt(text.substring(from, to));
    }

    private static String ownVersion() {
        try (InputStream in = ScaladocPlugin.class.getResourceAsStream("/scaladoc-gradle-plugin.properties")) {
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

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
 *   <li><b>Scala 2:</b> the compiler plugin, which embeds the scaladoc of the module's classes into their bytecode
 *       ({@code embed}, on by default); the {@code scaladoc} block's include and exclude patterns become its
 *       options.</li>
 *   <li><b>Scala 3:</b> {@code -Yread-docs}, which lets {@code Scaladoc.of} read doc comments from other modules and
 *       jars. Scala 3 keeps doc comments in TASTy on its own, so nothing else is needed.</li>
 * </ul>
 *
 * It adds no library: a module that reads scaladoc with {@code Scaladoc.of} depends on {@code scaladoc-parser} itself.
 * The Scala version is the one of the module's {@code scala-library} or {@code scala3-library_3} dependency.
 */
public class ScaladocPlugin implements Plugin<Project> {

    static final String GROUP = "io.github.andyglow";

    static final String COMPILER_PLUGIN = "scaladoc-compiler-plugin";

    // the configurations a build declares its Scala library in
    private static final List<String> DECLARING =
        Arrays.asList("api", "implementation", "compileOnly", "compileOnlyApi");

    @Override
    public void apply(Project project) {
        ScaladocExtension extension = project.getExtensions().create("scaladoc", ScaladocExtension.class);
        extension.getVersion().convention(ownVersion());
        extension.getEmbed().convention(true);
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
        String scala = scalaBinaryVersion(project);
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

        if (scala.equals("3")) {
            addCompilerOptions(project, Collections.singletonList("-Yread-docs"));
        } else if (extension.getEmbed().get()) {
            // the compiler plugin's jar has everything it needs, so the module needs nothing else
            dependencies.add("scalaCompilerPlugins", GROUP + ":" + COMPILER_PLUGIN + "_" + scala + ":" + version);
            addCompilerOptions(project, compilerPluginOptions(extension));
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

    /** {@code 3}, {@code 2.13} or {@code 2.12}, from the declared Scala library; {@code null} if there's none. */
    static String scalaBinaryVersion(Project project) {
        String scala2 = null;
        for (String name : DECLARING) {
            Configuration configuration = project.getConfigurations().findByName(name);
            if (configuration == null) continue;
            for (Dependency dependency : configuration.getDependencies()) {
                if (!"org.scala-lang".equals(dependency.getGroup())) continue;
                if ("scala3-library_3".equals(dependency.getName())) return "3";
                if ("scala-library".equals(dependency.getName()) && dependency.getVersion() != null) {
                    String[] parts = dependency.getVersion().split("\\.");
                    if (parts.length >= 2) scala2 = parts[0] + "." + parts[1];
                }
            }
        }
        return scala2;
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

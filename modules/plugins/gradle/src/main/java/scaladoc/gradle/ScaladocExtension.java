package scaladoc.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code scaladoc { ... }} block. The include and exclude patterns tune what the Scala 2 compiler plugin embeds;
 * see https://andyglow.github.io/scaladoc/compiler-plugin.html#choosing-what-to-embed.
 */
public abstract class ScaladocExtension {

    /** The version of the compiler plugin. The Gradle plugin's own by default. */
    public abstract Property<String> getVersion();

    /**
     * Scala 2: embed the scaladoc of this module's classes into their bytecode. {@code true} if not set.
     *
     * @deprecated since 0.5.1, removed in 0.6.0: a module that shouldn't embed scaladoc doesn't apply the plugin
     */
    @Deprecated
    public abstract Property<Boolean> getEmbed();

    /**
     * Adds {@code scaladoc-core} and {@code scaladoc-parser}. {@code false} by default.
     *
     * @deprecated since 0.5.1, removed in 0.6.0: a module that reads scaladoc depends on {@code scaladoc-parser}
     *     itself, like on any library it calls: {@code implementation("io.github.andyglow:scaladoc-parser_<scala>:<version>")}
     */
    @Deprecated
    public abstract Property<Boolean> getRead();

    /** Scala 2: embed only classes from source files matching these globs. */
    public abstract ListProperty<String> getIncludePaths();

    /** Scala 2: don't embed classes from source files matching these globs. */
    public abstract ListProperty<String> getExcludePaths();

    /** Scala 2: embed only classes whose fully qualified name matches these patterns. */
    public abstract ListProperty<String> getIncludeClasses();

    /** Scala 2: don't embed classes whose fully qualified name matches these patterns. */
    public abstract ListProperty<String> getExcludeClasses();
}

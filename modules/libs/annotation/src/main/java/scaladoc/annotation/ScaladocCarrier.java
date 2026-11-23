package scaladoc.annotation;

import java.lang.annotation.*;

/**
 * The scaladoc of a class, kept in its bytecode.
 *
 * <p>The scaladoc compiler plugin (Scala 2) puts it on every documented class it's told to embed, because the Scala 2
 * compiler drops doc comments otherwise. Its retention is {@code RUNTIME}, so it can be read:
 *
 * <ul>
 *   <li>at compile time, by {@code Scaladoc.of[T]} from {@code scaladoc-parser}, which parses it into a model</li>
 *   <li>at runtime, with Java reflection, from any JVM language, for example
 *       {@code SomeClass.class.getAnnotation(ScaladocCarrier.class).text()}</li>
 *   <li>by tools that read class files directly, which find it by its name</li>
 * </ul>
 *
 * <p>This is the only class in its jar, which has no dependencies, so a reader that processes the text its own way
 * needs nothing else. A module whose classes carry it depends on the jar, so that it reaches whatever uses them: the
 * JVM runs them without it, and Java reflection then leaves the annotation out, but Scala 3 doesn't compile against
 * them without it, since the annotation is part of their Scala signatures.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScaladocCarrier {

    /**
     * The doc comment as it is in the source. With {@link #tags()}, that's with its <code>/**</code> and
     * <code>*&#47;</code> markers and the {@code *} border of its lines, for example
     * <code>/** A user.\n  *\n  * &#64;param name its name\n  *&#47;</code>.
     */
    String text();

    /** Whether {@link #text()} has the comment's markers and border ({@code true}), or is just its content. */
    boolean tags();
}

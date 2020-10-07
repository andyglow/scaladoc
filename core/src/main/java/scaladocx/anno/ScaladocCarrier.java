package scaladocx.anno;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScaladocCarrier {
    String text();
    boolean tags();
}

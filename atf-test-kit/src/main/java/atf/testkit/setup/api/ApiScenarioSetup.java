package atf.testkit.setup.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Exact test references take precedence over consumer-defined families. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiScenarioSetup {
    String[] families() default {};
    ApiTestRef[] tests() default {};
}

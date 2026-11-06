package atf.testkit.setup.api;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** One exact consumer-selected API scenario method. */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiTestRef {
    Class<?> owner();
    String method();
}

package atf.testkit.reporting;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Opts a JUnit report owner into printing its final published Extent path. */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface AtfReportPath {
    Policy value() default Policy.ENABLED;

    enum Policy {
        ENABLED,
        DISABLED
    }
}

package atf.testkit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the diagnostic console detail level for a test class. */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface AtfConsoleMessages {
    Policy value() default Policy.INHERIT;

    enum Policy {
        /** Preserve reporting.console settings from atf.yaml or the ATF defaults. */
        INHERIT,
        /** Show lifecycle and the existing structured detail/code events. */
        DETAILS,
        /** Show lifecycle and verdicts, but suppress detail/code events. */
        SUMMARY,
        /** Suppress only the console projection for this test class. */
        OFF
    }
}

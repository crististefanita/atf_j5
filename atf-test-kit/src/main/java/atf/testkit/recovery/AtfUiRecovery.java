package atf.testkit.recovery;

import java.lang.annotation.*;

/** Invocation intent only; configuration remains the master switch and owns all budgets/allowlists. */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AtfUiRecovery {
    Mode value() default Mode.CONFIGURED;
    enum Mode { CONFIGURED, DISABLED, READ_ONLY }
}

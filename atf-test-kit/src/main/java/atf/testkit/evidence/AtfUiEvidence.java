package atf.testkit.evidence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares report evidence intent independently from the consumer's harness and renderer. */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AtfUiEvidence {
    AtfUiEvidenceFlow flow() default AtfUiEvidenceFlow.CONFIGURED;

    AtfUiScreenshotPolicy screenshots() default AtfUiScreenshotPolicy.CONFIGURED;

    AtfUiActionDetails actionDetails() default AtfUiActionDetails.CONFIGURED;
}

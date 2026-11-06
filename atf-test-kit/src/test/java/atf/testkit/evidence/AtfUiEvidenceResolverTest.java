package atf.testkit.evidence;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static atf.testkit.evidence.AtfUiActionDetails.SUMMARY_ONLY;
import static atf.testkit.evidence.AtfUiEvidenceFlow.UNIFIED_FLOW;
import static atf.testkit.evidence.AtfUiScreenshotPolicy.FINAL_AND_FAILURE;
import static atf.testkit.evidence.AtfUiScreenshotPolicy.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfComponentTest
class AtfUiEvidenceResolverTest {
    @Test
    void absentAndConfiguredRemainDistinctWithoutInventingAnOverride() {
        assertTrue(AtfUiEvidenceResolver.resolve(Object.class).isEmpty());
        assertEquals(AtfUiEvidencePlan.configured(), AtfUiEvidenceResolver.resolve(Configured.class).orElseThrow());
    }

    @Test
    void directComposedAndInheritedDeclarationsResolveTheSameIndependentAxes() {
        var expected = new AtfUiEvidencePlan(UNIFIED_FLOW, FINAL_AND_FAILURE, SUMMARY_ONLY);
        assertEquals(expected, AtfUiEvidenceResolver.resolve(Direct.class).orElseThrow());
        assertEquals(expected, AtfUiEvidenceResolver.resolve(Composed.class).orElseThrow());
        assertEquals(expected, AtfUiEvidenceResolver.resolve(Inherited.class).orElseThrow());
        assertEquals(AtfUiEvidencePlan.configured(), AtfUiEvidenceResolver.resolve(Reset.class).orElseThrow());
    }

    @Test
    void complementaryCompositionsMergeAndConflictingAxesFailWithTheOwner() {
        assertEquals(new AtfUiEvidencePlan(UNIFIED_FLOW, NONE, SUMMARY_ONLY),
                AtfUiEvidenceResolver.resolve(Complementary.class).orElseThrow());
        var failure = assertThrows(IllegalStateException.class, () -> AtfUiEvidenceResolver.resolve(Conflicting.class));
        assertTrue(failure.getMessage().contains(Conflicting.class.getName()));
        assertTrue(failure.getMessage().contains("screenshots"));
    }

    @AtfUiEvidence static class Configured { }
    @AtfUiEvidence(flow = UNIFIED_FLOW, screenshots = FINAL_AND_FAILURE, actionDetails = SUMMARY_ONLY)
    static class Direct { }
    static class Inherited extends Direct { }
    @AtfUiEvidence static class Reset extends Direct { }
    @Compact @Terminal static class Composed { }
    @Compact @AtfUiEvidence(screenshots = NONE) static class Complementary { }
    @Terminal @AtfUiEvidence(screenshots = NONE) static class Conflicting { }
    @Retention(RetentionPolicy.RUNTIME) @AtfUiEvidence(flow = UNIFIED_FLOW, actionDetails = SUMMARY_ONLY)
    @interface Compact { }
    @Retention(RetentionPolicy.RUNTIME) @AtfUiEvidence(screenshots = FINAL_AND_FAILURE)
    @interface Terminal { }
}

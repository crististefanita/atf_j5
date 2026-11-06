package atf.testkit.reporting;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfUnitTest
class AtfReportPathPolicyResolverTest {
    @Test
    void absenceDisablesAndDirectDeclarationOptsIn() {
        assertEquals(AtfReportPath.Policy.DISABLED,
                AtfReportPathPolicyResolver.resolve(Unconfigured.class));
        assertEquals(AtfReportPath.Policy.ENABLED,
                AtfReportPathPolicyResolver.resolve(DirectOptIn.class));
    }

    @Test
    void policySupportsMetaAnnotationsAndNearestSuperclassOverride() {
        assertEquals(AtfReportPath.Policy.ENABLED,
                AtfReportPathPolicyResolver.resolve(MetaOptIn.class));
        assertEquals(AtfReportPath.Policy.DISABLED,
                AtfReportPathPolicyResolver.resolve(ExplicitOptOut.class));
    }

    @Test
    void conflictingComposedPoliciesFailBeforeReportSetup() {
        assertThrows(IllegalStateException.class,
                () -> AtfReportPathPolicyResolver.resolve(Conflicting.class));
    }

    @Test
    void directPolicyOverridesComposedDefaultsInBothDirections() {
        assertEquals(AtfReportPath.Policy.DISABLED, AtfReportPathPolicyResolver.resolve(DirectDisable.class));
        assertEquals(AtfReportPath.Policy.ENABLED, AtfReportPathPolicyResolver.resolve(DirectEnable.class));
        assertEquals(AtfReportPath.Policy.DISABLED, AtfReportPathPolicyResolver.resolve(DirectOverConflict.class));
    }

    static class Unconfigured { }

    @AtfReportPath
    static class DirectOptIn { }

    @TestReportPathOwner
    static class MetaOptIn { }

    @AtfReportPath
    static class InheritedOwner { }

    @AtfReportPath(AtfReportPath.Policy.DISABLED)
    static class ExplicitOptOut extends InheritedOwner { }

    @TestReportPathOwner
    @TestDisabledReportPathOwner
    static class Conflicting { }

    @AtfReportPath(AtfReportPath.Policy.DISABLED) @TestReportPathOwner
    static class DirectDisable { }
    @AtfReportPath @TestDisabledReportPathOwner
    static class DirectEnable { }
    @AtfReportPath(AtfReportPath.Policy.DISABLED) @TestReportPathOwner @TestDisabledReportPathOwner
    static class DirectOverConflict { }
}

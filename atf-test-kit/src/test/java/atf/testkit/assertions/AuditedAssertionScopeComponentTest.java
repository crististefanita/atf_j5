package atf.testkit.assertions;

import atf.testkit.AtfComponentTest;
import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@AtfComponentTest
class AuditedAssertionScopeComponentTest {
    @Test
    void nestedJupiterFailuresDoNotContaminateTheOuterComponentAudit() {
        var rows = new RecordedAssertions();
        try (var scope = AuditedAssertionScope.open(getClass().getName(),
                "nestedJupiterFailuresDoNotContaminateTheOuterComponentAudit", rows)) {
            var summary = new SummaryGeneratingListener();
            LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(NestedFixture.class)).build(), summary);
            assertTrue(rows.checks().isEmpty());
            AuditedAssertions.assertEquals(1, summary.getSummary().getTestsFailedCount());
        }
        assertEquals(1, rows.checks().size());
        assertTrue(rows.checks().getFirst().passed());
    }

    @AtfUnitTest
    static class NestedFixture {
        @Test void intentionalFailure() { AuditedAssertions.assertTrue(false); }
    }
}

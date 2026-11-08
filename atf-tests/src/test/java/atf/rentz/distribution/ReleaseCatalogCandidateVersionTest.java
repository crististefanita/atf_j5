package atf.rentz.distribution;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class ReleaseCatalogCandidateVersionTest {
    @Test void acceptsOneMinorOrMajorBoundaryForRefactorsAndFeatureBatches() {
        assertAll(
                () -> assertTrue(ReleaseCatalog.isMinorOrMajorStep("0.3.0", "0.4.0")),
                () -> assertTrue(ReleaseCatalog.isMinorOrMajorStep("1.2.8", "1.3.0")),
                () -> assertTrue(ReleaseCatalog.isMinorOrMajorStep("0.9.7", "1.0.0")),
                () -> assertTrue(ReleaseCatalog.isMinorOrMajorStep("1.8.4", "2.0.0")));
    }

    @Test void rejectsPatchStepsSkippedBoundariesAndNonForwardCandidates() {
        assertAll(
                () -> assertFalse(ReleaseCatalog.isMinorOrMajorStep("0.3.0", "0.3.1")),
                () -> assertFalse(ReleaseCatalog.isMinorOrMajorStep("1.2.0", "1.4.0")),
                () -> assertFalse(ReleaseCatalog.isMinorOrMajorStep("1.2.0", "3.0.0")),
                () -> assertFalse(ReleaseCatalog.isMinorOrMajorStep("1.2.0", "1.2.0")),
                () -> assertFalse(ReleaseCatalog.isMinorOrMajorStep("1.2.0", "1.1.0")),
                () -> assertFalse(ReleaseCatalog.isMinorOrMajorStep("invalid", "1.0.0")));
    }
}

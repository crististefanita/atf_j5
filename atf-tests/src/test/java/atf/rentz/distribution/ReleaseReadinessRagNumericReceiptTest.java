package atf.rentz.distribution;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class ReleaseReadinessRagNumericReceiptTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void receiptCountsAcceptOnlyExactWholeNumbersWithinIntegerRange() throws Exception {
        assertEquals(2, ReleaseReadinessRag.exactCount(JSON.readTree("2")));
        assertEquals(2, ReleaseReadinessRag.exactCount(JSON.readTree("2.0")));
        assertThrows(java.io.IOException.class, () -> ReleaseReadinessRag.exactCount(JSON.readTree("2.5")));
        assertThrows(java.io.IOException.class, () -> ReleaseReadinessRag.exactCount(JSON.readTree("2147483648")));
        assertThrows(java.io.IOException.class, () -> ReleaseReadinessRag.exactCount(JSON.readTree("\"2\"")));
    }
}

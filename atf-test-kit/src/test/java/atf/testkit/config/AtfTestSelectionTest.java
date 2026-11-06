package atf.testkit.config;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfUnitTest
class AtfTestSelectionTest {
    @Test
    void parsesAndRebuildsAnExactClassMethodSelector() {
        var selection = AtfTestSelection.parse("atf.api.tests.UsersApiScenarios#readUser");

        assertEquals("atf.api.tests.UsersApiScenarios", selection.className());
        assertEquals("readUser", selection.methodName());
        assertEquals("atf.api.tests.UsersApiScenarios#readUser", selection.selector());
    }

    @Test
    void rejectsAmbiguousSelectors() {
        assertThrows(IllegalArgumentException.class,
                () -> AtfTestSelection.parse("UsersApiScenarios.readUser"));
    }
}

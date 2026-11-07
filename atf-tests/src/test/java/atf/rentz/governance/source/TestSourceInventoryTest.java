package atf.rentz.governance.source;

import atf.rentz.discovery.TestSourceInventory;
import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class TestSourceInventoryTest {
    @TempDir Path root;
    static class ActualParameterizedFixture {
        @ParameterizedTest @ValueSource(ints={1})
        void runs(int number) { assertEquals(1, number); }
    }

    @Test void realJupiterParameterizedCaseAndSourceInventoryAgreeOnExecutability() throws Exception {
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(ActualParameterizedFixture.class)).build(), listener);
        assertEquals(1, listener.getSummary().getTestsSucceededCount());
        assertEquals(0, listener.getSummary().getTestsFailedCount());
        Path sources = Files.createDirectories(root.resolve("src/p"));
        Files.writeString(sources.resolve("Nonconventional.java"),
                "package p; @atf.testkit.AtfComponentTest class Nonconventional { @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={1}) void runs(int n) {} }");
        var inventory = TestSourceInventory.scan(root, List.of(root.resolve("src")));
        assertEquals(1, inventory.size());
        assertTrue(inventory.getFirst().executable());
        assertEquals("p.Nonconventional", inventory.getFirst().name());
        assertEquals(List.of("atf.testkit.AtfComponentTest"), inventory.getFirst().layers());
    }

    @Test void compositionMayBeDeclaredInMainWhileHelpersAndAnnotationsRemainMetadata() throws Exception {
        Path main = Files.createDirectories(root.resolve("main/p"));
        Path tests = Files.createDirectories(root.resolve("test/p"));
        Files.writeString(main.resolve("CaseMarker.java"), "package p; @org.junit.jupiter.api.Test @interface CaseMarker {}");
        Files.writeString(tests.resolve("ActualTest.java"), "package p; @atf.testkit.AtfComponentTest class ActualTest { @CaseMarker void runs() {} }");
        Files.writeString(tests.resolve("MarkerTest.java"), "package p; @interface MarkerTest {}");
        Files.writeString(tests.resolve("Helper.java"), "package p; class Helper { String text = \"@org.junit.jupiter.api.Test\"; }");
        var inventory = TestSourceInventory.scan(root, List.of(root.resolve("main"), root.resolve("test")));
        assertEquals(List.of("p.ActualTest"), inventory.stream().filter(TestSourceInventory.Entry::executable).map(TestSourceInventory.Entry::name).toList());
        assertThrows(IllegalArgumentException.class, () -> TestSourceInventory.scan(root, List.of(root.resolve("test"), root.resolve("test"))));
    }
}

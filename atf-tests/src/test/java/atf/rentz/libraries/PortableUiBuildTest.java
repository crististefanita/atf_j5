package atf.rentz.libraries;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class PortableUiBuildTest {
    private static final List<String> PORTABLE = List.of("score/ScoreTableContent", "score/HistoryContent",
            "score/HistoryGridMetrics", "score/HistoryScrollSync", "screens/GameTitleLayout", "seat/SeatTypography");
    @Test void androidAndBrowserPinTheSamePortablePresentationSources() throws Exception {
        Path root = LibraryAdoptionTest.root();
        var json = new ObjectMapper();
        sameInputs(json.readTree(root.resolve("rentz-lab-app/ui-library/ro/cardtable/card-table-ui/0.1.21/card-ui-build.json").toFile()),
                json.readTree(root.resolve("atf-ui-browser/lib/browser-build.json").toFile()));
    }
    @Test void aDifferentPortableInputCannotPassCrossPlatformQualification() throws Exception {
        Path root = LibraryAdoptionTest.root();
        var json = new ObjectMapper();
        var android = json.readTree(root.resolve("rentz-lab-app/ui-library/ro/cardtable/card-table-ui/0.1.21/card-ui-build.json").toFile());
        var browser = json.readTree(root.resolve("atf-ui-browser/lib/browser-build.json").toFile());
        sameInputs(android, browser);
        var changed = browser.deepCopy();
        String path = portablePath(PORTABLE.getFirst());
        var input = java.util.stream.StreamSupport.stream(changed.required("inputs").spliterator(), false)
                .filter(row -> path.equals(row.required("path").asText())).findFirst().orElseThrow();
        ((ObjectNode) input).put("sha256", "0".repeat(64));
        assertThrows(AssertionError.class, () -> sameInputs(android, changed), "A changed model cannot pass by keeping the library version");
    }
    private static void sameInputs(JsonNode android, JsonNode browser) {
        for (String type : PORTABLE) {
            String path = portablePath(type);
            assertEquals(inputHash(android, path), inputHash(browser, path), "Android and browser pin the same portable source " + type);
        }
    }
    private static String portablePath(String type) {
        return "whist-lab-app/atf-card-table-ui/src/main/java/ro/cardtable/ui/" + type + ".java";
    }
    private static String inputHash(JsonNode receipt, String path) {
        return java.util.stream.StreamSupport.stream(receipt.required("inputs").spliterator(), false)
                .filter(row -> path.equals(row.required("path").asText())).map(row -> row.required("sha256").asText())
                .findFirst().orElseThrow(() -> new AssertionError("Missing portable source: " + path));
    }
    @Test void pagesWorkflowCoversEveryDeclaredPortableNativeJavaInput() throws Exception {
        Path root = LibraryAdoptionTest.root();
        String starter = Files.readString(root.resolve("atf-ui-browser/Start-RentzBrowser.ps1"));
        var imported = Pattern.compile("Join-Path \\$checkout '([^']+\\.java)'").matcher(starter).results().map(m -> m.group(1)).toList();
        assertTrue(imported.size() >= 10, "The source inventory cannot pass as an empty scan");
        String workflow = Files.readString(root.resolve(".github/workflows/rentz-pages.yml"));
        String paths = workflow.substring(workflow.indexOf("    paths:"), workflow.indexOf("  workflow_dispatch:"));
        var filters = Pattern.compile("(?m)^      - ([^\\r\\n]+)$").matcher(paths).results().map(m -> m.group(1).strip()).toList();
        assertEquals(List.of(), uncovered(imported, filters), "Changing any compiled native projection must qualify and rebuild WEB");
    }
    @Test void aProfileOnlyTriggerCannotAdmitScoreOrTableProjectionChanges() {
        var inputs = List.of("rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/score/RentzScoreContent.java",
                "rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/table/RentzTableContent.java");
        var old = List.of("atf-ui-browser/**", "rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/profile/DisplayName.java");
        assertEquals(inputs, uncovered(inputs, old), "Both consumer-owned projections need an explicit trigger");
    }
    private static List<String> uncovered(List<String> inputs, List<String> filters) {
        var matchers = filters.stream().map(filter -> FileSystems.getDefault().getPathMatcher("glob:" + filter)).toList();
        return inputs.stream().filter(path -> matchers.stream().noneMatch(matcher -> matcher.matches(Path.of(path)))).toList();
    }
}

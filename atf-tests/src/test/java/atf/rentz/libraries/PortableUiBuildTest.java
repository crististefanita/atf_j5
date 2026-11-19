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
    private static final List<String> PORTABLE = List.of("score/ScoreTableContent", "score/ScoreTypography", "score/HistoryContent",
            "score/HistoryGridMetrics", "score/HistoryScrollSync", "screens/GameTitleLayout", "seat/SeatTypography");
    @Test void androidAndBrowserPinTheSamePortablePresentationSources() throws Exception {
        Path root = LibraryAdoptionTest.root();
        var json = new ObjectMapper();
        sameInputs(nativeReceipt(root, json),
                json.readTree(root.resolve("atf-ui-browser/lib/browser-build.json").toFile()));
    }
    @Test void aDifferentPortableInputCannotPassCrossPlatformQualification() throws Exception {
        Path root = LibraryAdoptionTest.root();
        var json = new ObjectMapper();
        var android = nativeReceipt(root, json);
        var browser = json.readTree(root.resolve("atf-ui-browser/lib/browser-build.json").toFile());
        sameInputs(android, browser);
        for (String type : PORTABLE) {
            var changed = browser.deepCopy();
            String path = portablePath(type);
            var input = java.util.stream.StreamSupport.stream(changed.required("inputs").spliterator(), false)
                    .filter(row -> path.equals(row.required("path").asText())).findFirst().orElseThrow();
            ((ObjectNode) input).put("sha256", "0".repeat(64));
            assertThrows(AssertionError.class, () -> sameInputs(android, changed),
                    "A changed portable model cannot pass by keeping the library version: " + type);
        }
    }

    private static JsonNode nativeReceipt(Path root, ObjectMapper json) throws Exception {
        Path repository = root.resolve("rentz-lab-app/ui-library");
        var pin = json.readTree(repository.resolve("provenance.json").toFile());
        String coordinate = pin.required("coordinate").asText();
        assertTrue(coordinate.matches("ro[.]cardtable:card-table-ui:[0-9]+[.][0-9]+[.][0-9]+"), "Explicit native coordinate");
        String relative = "ro/cardtable/card-table-ui/" + coordinate.substring(coordinate.lastIndexOf(':') + 1) + "/card-ui-build.json";
        var hashes = java.util.stream.StreamSupport.stream(pin.required("files").spliterator(), false)
                .filter(row -> relative.equals(row.required("path").asText())).toList();
        assertEquals(1, hashes.size(), "The admitted native build receipt must be pinned exactly once");
        byte[] bytes = Files.readAllBytes(repository.resolve(relative));
        assertEquals(hashes.getFirst().required("sha256").asText(),
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),
                "Read the exact native producer receipt");
        var receipt = json.readTree(bytes);
        assertEquals(coordinate, receipt.required("coordinate").asText(), "The receipt belongs to the admitted native version");
        return receipt;
    }

    @Test void nativeBuildReceiptCannotBeUnpinnedOrReplaced(@org.junit.jupiter.api.io.TempDir Path fixture) throws Exception {
        Path root = LibraryAdoptionTest.root(), repository = fixture.resolve("rentz-lab-app/ui-library");
        Files.createDirectories(repository);
        var json = new ObjectMapper();
        var pin = (ObjectNode) json.readTree(root.resolve("rentz-lab-app/ui-library/provenance.json").toFile());
        var missing = pin.deepCopy();
        missing.putArray("files");
        json.writeValue(repository.resolve("provenance.json").toFile(), missing);
        assertThrows(AssertionError.class, () -> nativeReceipt(fixture, json), "A version alone cannot identify its source receipt");
        json.writeValue(repository.resolve("provenance.json").toFile(), pin);
        String relative = java.util.stream.StreamSupport.stream(pin.required("files").spliterator(), false)
                .map(row -> row.required("path").asText()).filter(path -> path.endsWith("/card-ui-build.json"))
                .findFirst().orElseThrow();
        Path target = repository.resolve(relative);
        Files.createDirectories(target.getParent());
        byte[] original = Files.readAllBytes(root.resolve("rentz-lab-app/ui-library").resolve(relative));
        Files.write(target, original);
        assertEquals(pin.required("coordinate").asText(), nativeReceipt(fixture, json).required("coordinate").asText(),
                "The exact original receipt remains valid");
        Files.writeString(target, new String(original, java.nio.charset.StandardCharsets.UTF_8) + " ");
        assertThrows(AssertionError.class, () -> nativeReceipt(fixture, json), "Even valid JSON cannot replace the pinned receipt bytes");
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

package atf.rentz.governance.source;

import atf.testkit.AtfComponentTest;
import atf.rentz.discovery.TestLayers;
import atf.rentz.discovery.TestSourceInventory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class TestLayerGovernanceTest {
    @Test void everyExecutableTestDeclaresExactlyOneLayerForItsBuild() throws Exception {
        var root = GovernanceProject.root();
        var layout = new ObjectMapper().readTree(root.resolve("config/java-source-layout.json").toFile());
        var builds = new java.util.TreeMap<String, List<java.nio.file.Path>>();
        var tests = new java.util.HashSet<java.nio.file.Path>();
        var external = new java.util.HashSet<String>();
        layout.path("externalNonTestBases").forEach(value -> external.add(value.asText()));
        for (var entry : layout.required("roots")) {
            String build = entry.path("build").asText("native");
            assertTrue(build.matches("[a-z][a-z0-9-]*"), "Invalid source build");
            if (!entry.required("role").asText().equals("tools")) builds.computeIfAbsent(build, ignored -> new ArrayList<>()).add(root.resolve(entry.required("path").asText()));
            if (entry.required("role").asText().equals("test")) tests.add(root.resolve(entry.required("path").asText()));
        }
        var findings = new ArrayList<String>();
        int executable = 0;
        for (var roots : builds.values()) for (var type : TestSourceInventory.scan(root, roots, tests, external)) {
            String relative = type.file();
            boolean testRoot = false;
            for (var entry : layout.required("roots")) {
                if (entry.required("role").asText().equals("test") && relative.startsWith(entry.required("path").asText() + "/")) testRoot = true;
            }
            if (!testRoot || !type.executable()) continue;
            executable++;
            boolean allowed = type.layers().size() == 1;
            if (allowed && relative.startsWith("rentz-lab-app/game-core/")) {
                allowed = type.layers().getFirst().equals("ro.rentzlab.testing.RentzComponentLayer");
            } else if (allowed && relative.startsWith("rentz-lab-app/app/")) {
                allowed = type.layers().getFirst().equals("ro.rentzlab.app.testing.AndroidComponentLayer");
            } else if (allowed) {
                allowed = type.layers().getFirst().startsWith("atf.testkit.");
            }
            if (!allowed) findings.add(relative + "#" + type.name() + " layers=" + type.layers());
        }
        assertTrue(executable > 0, "Empty inventory cannot qualify test layers");
        assertEquals(List.of(), findings, "GOV-TEST-LAYER: executable tests need an unambiguous build-specific layer");
    }

    @Test void buildVariantsRemainInventoriedButDuplicatesInOneBuildStillFail(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        var nativeRoot=root.resolve("native");
        var browserRoot=root.resolve("browser");
        for(var source:List.of(nativeRoot,browserRoot)) {
            java.nio.file.Files.createDirectories(source);
            java.nio.file.Files.writeString(source.resolve("Name.java"),"package profile; public final class Name {}");
        }
        assertEquals(1,TestSourceInventory.scan(root,List.of(nativeRoot)).size());
        assertEquals(1,TestSourceInventory.scan(root,List.of(browserRoot)).size());
        assertThrows(IllegalArgumentException.class,()->TestSourceInventory.scan(root,List.of(nativeRoot,browserRoot)));
    }

    @Test void qualifiedComposedAndModifierAnnotationsResolveWithoutLabelingFixtures() {
        String test = "package p; public @atf.testkit.AtfComponentTest class Case { @org.junit.jupiter.api.Test void run() {} }";
        assertEquals(List.of("atf.testkit.AtfComponentTest"), TestLayers.inspect(test, Map.of()).getFirst().layers());
        var nestedMarker = TestLayers.annotations(List.of(
                "package p; class Holder { @atf.testkit.AtfComponentTest @interface Component {} }"));
        var relative = TestLayers.inspect(
                "package demo; import p.Holder; @Holder.Component class Case { @org.junit.jupiter.api.Test void x() {} }",
                nestedMarker);
        assertEquals(List.of("atf.testkit.AtfComponentTest"), relative.getFirst().layers());
        var meta = Map.of("p.Layer", "package p; @atf.testkit.AtfUnitTest @interface Layer {}");
        assertEquals(List.of("atf.testkit.AtfUnitTest"), TestLayers.inspect(
                test.replace("@atf.testkit.AtfComponentTest", "@Layer"), meta).getFirst().layers());
        var helper = TestLayers.inspect("class Helper { class Fixture { @org.junit.jupiter.api.Test void x() {} } }", Map.of());
        assertFalse(helper.getFirst().executable());
        var nested = TestLayers.inspect(test.replace("void run() {}", "void run() {} @org.junit.jupiter.api.Nested class Inner { @org.junit.jupiter.api.Test void y() {} }"), Map.of());
        assertEquals(2, nested.size());
        assertEquals(nested.getFirst().layers(), nested.getLast().layers());
    }

    @Test void foreignAmbiguousDoubleAndCyclicMarkersCannotQualifyTests() {
        String unit = "package p; %s class Case { @org.junit.jupiter.api.Test void run() {} }";
        assertEquals(List.of(), TestLayers.inspect(unit.formatted("@foreign.AtfComponentTest"), Map.of()).getFirst().layers());
        assertEquals(2, TestLayers.inspect(unit.formatted("@atf.testkit.AtfUnitTest @atf.testkit.AtfComponentTest"), Map.of()).getFirst().layers().size());
        var metas = Map.of("a.Layer", "package a; @atf.testkit.AtfUnitTest @interface Layer {}",
                "b.Layer", "package b; @atf.testkit.AtfComponentTest @interface Layer {}");
        assertThrows(IllegalArgumentException.class, () -> TestLayers.inspect(
                "package p; import a.*; import b.*; @Layer class Case { @org.junit.jupiter.api.Test void x() {} }", metas));
        assertThrows(IllegalArgumentException.class, () -> TestLayers.inspect(unit.formatted("@Loop"),
                Map.of("p.Loop", "package p; @Loop @interface Loop {}")));
        assertFalse(TestLayers.inspect("class Fixture { @foreign.Test void helper() {} }", Map.of()).getFirst().executable());
    }
}

package atf.rentz.reporting;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class ComponentSuiteExtentReportTest {
    @TempDir Path temporary;

    @Test void publishesAnExplicitFailArtifactWhenExecutionStopsBeforeSurefireXml() throws Exception {
        Path report = temporary.resolve("empty-population.html");
        var summary = ComponentSuiteExtentReport.publish(report, "e".repeat(40), "f".repeat(64), "regression", 1,
                "Maven exited before emitting Surefire XML", List.of());

        assertEquals(0, summary.suites());
        assertEquals(0, summary.tests());
        assertFalse(summary.qualified());
        String html = Files.readString(report);
        assertTrue(html.contains("Maven exited before emitting Surefire XML"));
        assertTrue(html.contains("non-empty"));
        assertEquals(summary.reportSha256(), sha(report));
    }

    @Test void qualifiedSuiteProducesAnAuditablePassingExtentArtifact() throws Exception {
        Path xml = temporary.resolve("TEST-sample.QualifiedTest.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="sample.QualifiedTest" tests="2" failures="0" errors="0" skipped="0">
                  <testcase classname="sample.QualifiedTest" name="legal action is accepted" time="0.02"/>
                  <testcase classname="sample.QualifiedTest" name="illegal action is rejected" time="0.01"/>
                </testsuite>
                """);
        Path report = temporary.resolve("qualified-extent.html");

        var summary = ComponentSuiteExtentReport.publish(report, "c".repeat(40), "d".repeat(64), "regression", 0,
                List.of(new ComponentSuiteExtentReport.SuiteInput("game-core", "sample.QualifiedTest", xml, sha(xml), 2, 0, 0, 0)));

        assertTrue(summary.qualified());
        assertEquals(2, summary.tests());
        assertEquals(0, summary.failures() + summary.errors() + summary.skipped());
        String html = Files.readString(report);
        assertTrue(html.contains("legal action is accepted"));
        assertTrue(html.contains("illegal action is rejected"));
        assertTrue(html.contains("sourceManifestSha256=d" + "d".repeat(63)));
        assertEquals(summary.reportSha256(), sha(report));
    }

    @Test void extentReceiptCoversEachSurefireCaseAndReconcilesAggregateCounts() throws Exception {
        Path xml = temporary.resolve("TEST-sample.ContractTest.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="sample.ContractTest" tests="3" failures="1" errors="0" skipped="1">
                  <testcase classname="sample.ContractTest" name="accepts legal play" time="0.02"/>
                  <testcase classname="sample.ContractTest" name="rejects illegal play" time="0.01">
                    <failure message="expected rejection">controlled failure detail</failure>
                  </testcase>
                  <testcase classname="sample.ContractTest" name="excluded scenario" time="0.00"><skipped/></testcase>
                </testsuite>
                """);
        Path report = temporary.resolve("component-suite-extent.html");

        var summary = ComponentSuiteExtentReport.publish(report, "a".repeat(40), "b".repeat(64), "regression", 1,
                List.of(new ComponentSuiteExtentReport.SuiteInput("atf-tests", "sample.ContractTest", xml, sha(xml), 3, 1, 0, 1)));

        assertEquals(1, summary.suites());
        assertEquals(3, summary.tests());
        assertEquals(1, summary.failures());
        assertEquals(0, summary.errors());
        assertEquals(1, summary.skipped());
        assertFalse(summary.qualified());
        String html = Files.readString(report);
        assertTrue(html.contains("accepts legal play"));
        assertTrue(html.contains("rejects illegal play"));
        assertTrue(html.contains("excluded scenario"));
        assertTrue(html.contains("controlled failure detail"));
        assertEquals(summary.reportSha256(), sha(report));
        assertTrue(Files.size(report) > 10_000, "Extent report is a complete standalone artifact");
    }

    @Test void reportRefusesXmlPopulationThatDoesNotMatchTheRunnerReceipt() throws Exception {
        Path xml = temporary.resolve("TEST-sample.EmptyTest.xml");
        Files.writeString(xml, "<testsuite name=\"sample.EmptyTest\" tests=\"0\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");
        Path report = temporary.resolve("population-mismatch.html");

        assertThrows(java.io.IOException.class, () -> ComponentSuiteExtentReport.publish(report, "a".repeat(40), "b".repeat(64),
                "regression", 0, List.of(new ComponentSuiteExtentReport.SuiteInput("atf-tests", "sample.EmptyTest", xml,
                        sha(xml), 1, 0, 0, 0))));
    }

    private static String sha(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}

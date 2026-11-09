package atf.rentz.reporting;

import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportPorts;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.artifact.ReportArtifactInspector;
import atf.reporting.artifact.ReportArtifactProfile;
import atf.reporting.extent.session.ExtentReportSession;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Publishes the selected Surefire population through the pinned atf-reporting Extent API. */
public final class ComponentSuiteExtentReport {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern COMMIT = Pattern.compile("[a-f0-9]{40}");
    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");
    private static final long MAX_INPUT_BYTES = 5L * 1024 * 1024;
    private static final long MAX_XML_BYTES = 24L * 1024 * 1024;

    public record SuiteInput(String module, String suiteName, Path xmlFile, String sha256,
                             int tests, int failures, int errors, int skipped) { }
    public record Summary(int suites, int tests, int failures, int errors, int skipped,
                          String reportSha256, boolean qualified) { }
    private record Case(String className, String name, String outcome, String detail) { }
    private record ParsedSuite(String name, List<Case> cases, int failures, int errors, int skipped) { }

    private ComponentSuiteExtentReport() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: ComponentSuiteExtentReport <receipt.json> <fresh-report.html>");
        Path input = Path.of(args[0]).toRealPath();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(input) || !Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)
                || Files.size(input) > MAX_INPUT_BYTES) throw new IOException("COMPONENT-EXTENT-INPUT");
        JsonNode root = JSON.readTree(Files.readAllBytes(input));
        if (!root.isObject() || root.size() != 7 || !root.path("schemaVersion").isIntegralNumber()
                || root.path("schemaVersion").intValue() != 1 || !root.path("suites").isArray())
            throw new IOException("COMPONENT-EXTENT-SCHEMA");
        List<SuiteInput> suites = new ArrayList<>();
        for (JsonNode row : root.path("suites")) {
            if (!row.isObject() || row.size() != 8) throw new IOException("COMPONENT-EXTENT-SUITE");
            suites.add(new SuiteInput(row.path("module").asText(), row.path("name").asText(),
                    Path.of(row.path("xml").asText()), row.path("sha256").asText(),
                    exactInt(row.path("tests")), exactInt(row.path("failures")),
                    exactInt(row.path("errors")), exactInt(row.path("skipped"))));
        }
        Summary summary = publish(output, root.path("sourceCommit").asText(), root.path("sourceManifestSha256").asText(),
                root.path("collection").asText(), exactInt(root.path("processExitCode")), root.path("executionFailure").asText(), suites);
        System.out.println(JSON.writeValueAsString(summary));
    }

    public static Summary publish(Path output, String sourceCommit, String sourceManifestSha256,
                                  String collection, int processExitCode, List<SuiteInput> inputs) throws IOException {
        return publish(output, sourceCommit, sourceManifestSha256, collection, processExitCode, "", inputs);
    }

    public static Summary publish(Path output, String sourceCommit, String sourceManifestSha256,
                                  String collection, int processExitCode, String executionFailure,
                                  List<SuiteInput> inputs) throws IOException {
        if (sourceCommit == null || !COMMIT.matcher(sourceCommit.toLowerCase(Locale.ROOT)).matches()
                || sourceManifestSha256 == null || !SHA256.matcher(sourceManifestSha256.toLowerCase(Locale.ROOT)).matches()
                || collection == null || collection.isBlank() || processExitCode < 0 || inputs == null)
            throw new IOException("COMPONENT-EXTENT-IDENTITY");
        if (output == null || output.getFileName() == null || Files.exists(output, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(output.toAbsolutePath().normalize())) throw new IOException("COMPONENT-EXTENT-OUTPUT");

        List<ParsedSuite> parsed = new ArrayList<>();
        Set<String> suiteKeys = new HashSet<>();
        Set<String> caseKeys = new HashSet<>();
        int testCount = 0, failureCount = 0, errorCount = 0, skippedCount = 0;
        for (SuiteInput input : inputs) {
            if (input == null || input.module() == null || input.module().isBlank() || input.suiteName() == null
                    || input.suiteName().isBlank() || input.tests() < 0 || input.failures() < 0
                    || input.errors() < 0 || input.skipped() < 0 || !SHA256.matcher(input.sha256()).matches())
                throw new IOException("COMPONENT-EXTENT-SUITE-IDENTITY");
            String suiteKey = input.module() + "\u0000" + input.suiteName();
            if (!suiteKeys.add(suiteKey)) throw new IOException("COMPONENT-EXTENT-DUPLICATE-SUITE");
            Path xml = input.xmlFile().toAbsolutePath().normalize();
            if (Files.isSymbolicLink(xml) || !Files.isRegularFile(xml, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(xml) == 0 || Files.size(xml) > MAX_XML_BYTES)
                throw new IOException("COMPONENT-EXTENT-XML-MISSING");
            if (!sha256(Files.readAllBytes(xml)).equalsIgnoreCase(input.sha256())) throw new IOException("COMPONENT-EXTENT-XML-HASH");
            ParsedSuite suite = parse(xml);
            if (!suite.name().equals(input.suiteName()) || suite.cases().size() != input.tests()
                    || suite.failures() != input.failures() || suite.errors() != input.errors()
                    || suite.skipped() != input.skipped()) throw new IOException("COMPONENT-EXTENT-POPULATION-MISMATCH");
            for (Case test : suite.cases()) {
                String caseKey = input.module() + "\u0000" + test.className() + "\u0000" + test.name();
                if (!caseKeys.add(caseKey)) throw new IOException("COMPONENT-EXTENT-DUPLICATE-CASE");
            }
            parsed.add(suite);
            testCount += suite.cases().size(); failureCount += suite.failures();
            errorCount += suite.errors(); skippedCount += suite.skipped();
        }

        Path parent = output.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent))
            throw new IOException("COMPONENT-EXTENT-PARENT");
        boolean qualified = !parsed.isEmpty() && testCount > 0 && processExitCode == 0
                && failureCount == 0 && errorCount == 0 && skippedCount == 0;
        try (AtfReportPorts.Session report = new ExtentReportSession(output, new ReportingSettings(240, 500))) {
            try (AtfReportPorts.Scenario scenario = report.startScenario("Component suite receipt",
                    rootDescription(executionFailure),
                    List.of("component", "surefire", "atf-reporting"));
                 AtfReportPorts.Phase phase = scenario.startPhase("Candidate and population")) {
                String expected = "commit=" + sourceCommit + "; sourceManifestSha256=" + sourceManifestSha256 + "; collection=" + collection;
                String actual = "commit=" + sourceCommit + "; sourceManifestSha256=" + sourceManifestSha256
                        + "; collection=" + collection + "; suites=" + parsed.size() + "; tests=" + testCount
                        + "; failures=" + failureCount + "; errors=" + errorCount + "; skipped=" + skippedCount
                        + "; processExitCode=" + processExitCode
                        + (executionFailure == null || executionFailure.isBlank() ? "" : "; executionFailure=" + safeText(executionFailure));
                AtfReportStatus status = qualified ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
                try (AtfReportPorts.AssertionStep checks = phase.startAssertionStep("Reconciliere receipt")) {
                    checks.assertions(List.of(new AtfAssertion("Component population", "non-empty; zero failure/error/skip; exitCode=0",
                            actual, qualified, status)));
                    checks.complete(status);
                }
                phase.detail("Source commit", sourceCommit);
                phase.detail("Source manifest SHA-256", sourceManifestSha256);
                phase.detail("Collection", collection);
                phase.detail("Receipt suites", Integer.toString(parsed.size()));
                phase.detail("Testcases", Integer.toString(testCount));
                if (executionFailure != null && !executionFailure.isBlank()) phase.detail("Execution diagnostic", safeText(executionFailure));
                phase.complete(status); scenario.complete(status);
            }
            for (int i = 0; i < inputs.size(); i++) publishSuite(report, inputs.get(i), parsed.get(i), caseKeys);
        }
        ReportArtifactProfile profile = profile(sourceCommit, sourceManifestSha256, collection, processExitCode,
                executionFailure, inputs, parsed, testCount, failureCount, errorCount, skippedCount, qualified);
        var inspection = ReportArtifactInspector.inspect(output, profile);
        if (!inspection.isCompliant()) throw new IOException("COMPONENT-EXTENT-INSPECTOR: " + inspection.violations());
        return new Summary(parsed.size(), testCount, failureCount, errorCount, skippedCount, sha256(Files.readAllBytes(output)), qualified);
    }

    private static String rootDescription(String executionFailure) {
        String base = "Selected component suites reconciled against original Surefire XML and the candidate source identity.";
        return executionFailure == null || executionFailure.isBlank() ? base : base + " Execution diagnostic: " + safeText(executionFailure);
    }

    private static ReportArtifactProfile profile(String commit, String manifestSha256, String collection, int processExitCode,
                                                  String executionFailure,
                                                  List<SuiteInput> inputs, List<ParsedSuite> parsed, int tests,
                                                  int failures, int errors, int skipped, boolean qualified) {
        List<ReportArtifactProfile.Scenario> scenarios = new ArrayList<>();
        AtfReportStatus totalStatus = qualified ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        String rootName = "Component suite receipt";
        String rootDescription = rootDescription(executionFailure);
        scenarios.add(new ReportArtifactProfile.Scenario(rootName, rootDescription, totalStatus, List.of(
                new ReportArtifactProfile.Phase("Candidate and population", totalStatus, List.of(
                        new ReportArtifactProfile.Assertions("Reconciliere receipt", List.of(
                                new ReportArtifactProfile.Comparison("Component population", totalStatus))))))));
        for (int i = 0; i < inputs.size(); i++) {
            SuiteInput input = inputs.get(i); ParsedSuite suite = parsed.get(i);
            AtfReportStatus suiteStatus = suite.failures() == 0 && suite.errors() == 0 && suite.skipped() == 0 && !suite.cases().isEmpty()
                    ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            String suiteName = input.module() + " :: " + suite.name();
            String source = input.xmlFile().getFileName() + " · SHA-256 " + input.sha256();
            String description = "Surefire XML is bound to the selected component receipt. " + source;
            scenarios.add(new ReportArtifactProfile.Scenario(suiteName, description, suiteStatus, List.of(
                    new ReportArtifactProfile.Phase("Suite totals", suiteStatus, List.of(
                            new ReportArtifactProfile.Assertions("Totals confruntate cu Surefire", List.of(
                                    new ReportArtifactProfile.Comparison("Suite totals", suiteStatus))))))));
            for (Case test : suite.cases()) {
                AtfReportStatus status = switch (test.outcome()) {
                    case "PASS" -> AtfReportStatus.PASS;
                    case "SKIP" -> AtfReportStatus.SKIP;
                    default -> AtfReportStatus.FAIL;
                };
                String name = input.module() + " :: " + test.className() + "#" + test.name();
                String testDescription = "Surefire testcase from " + source;
                scenarios.add(new ReportArtifactProfile.Scenario(name, testDescription, status, List.of(
                        new ReportArtifactProfile.Phase("Test body", status, List.of(
                                new ReportArtifactProfile.Assertions("Rezultat Surefire", List.of(
                                        new ReportArtifactProfile.Comparison("Testcase outcome", status))))))));
            }
        }
        return new ReportArtifactProfile(ReportArtifactProfile.Renderer.EXTENT, scenarios);
    }

    private static void publishSuite(AtfReportPorts.Session report, SuiteInput input, ParsedSuite suite, Set<String> caseKeys) {
        AtfReportStatus suiteStatus = suite.failures() == 0 && suite.errors() == 0 && suite.skipped() == 0 && !suite.cases().isEmpty()
                ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        String source = input.xmlFile().getFileName() + " · SHA-256 " + input.sha256();
        try (AtfReportPorts.Scenario scenario = report.startScenario(input.module() + " :: " + suite.name(),
                "Surefire XML is bound to the selected component receipt. " + source,
                List.of("component", "surefire", input.module()));
             AtfReportPorts.Phase phase = scenario.startPhase("Suite totals")) {
            String expected = "tests=" + input.tests() + "; failures=0; errors=0; skipped=0";
            String actual = "tests=" + suite.cases().size() + "; failures=" + suite.failures()
                    + "; errors=" + suite.errors() + "; skipped=" + suite.skipped();
            try (AtfReportPorts.AssertionStep checks = phase.startAssertionStep("Totals confruntate cu Surefire")) {
                checks.assertions(List.of(new AtfAssertion("Suite totals", expected, actual,
                        suiteStatus == AtfReportStatus.PASS, suiteStatus)));
                checks.complete(suiteStatus);
            }
            phase.detail("Original XML", input.xmlFile().toAbsolutePath().toString());
            phase.detail("Original XML SHA-256", input.sha256());
            phase.complete(suiteStatus); scenario.complete(suiteStatus);
        }
        for (Case test : suite.cases()) {
            AtfReportStatus status = switch (test.outcome()) {
                case "PASS" -> AtfReportStatus.PASS;
                case "SKIP" -> AtfReportStatus.SKIP;
                default -> AtfReportStatus.FAIL;
            };
            String name = input.module() + " :: " + test.className() + "#" + test.name();
            String actual = test.outcome() + (test.detail().isBlank() ? "" : " — " + test.detail());
            try (AtfReportPorts.Scenario scenario = report.startScenario(name, "Surefire testcase from " + source,
                    List.of("component", input.module(), test.outcome().toLowerCase(Locale.ROOT)));
                 AtfReportPorts.Phase phase = scenario.startPhase("Test body");
                 AtfReportPorts.AssertionStep checks = phase.startAssertionStep("Rezultat Surefire")) {
                checks.assertions(List.of(new AtfAssertion("Testcase outcome", "PASS", actual,
                        test.outcome().equals("PASS"), status)));
                phase.detail("Surefire XML", input.xmlFile().getFileName().toString());
                phase.detail("Surefire XML SHA-256", input.sha256());
                checks.complete(status); phase.complete(status); scenario.complete(status);
            }
        }
    }

    private static ParsedSuite parse(Path file) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            Element root = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
            if (!root.getTagName().equals("testsuite")) throw new IOException("COMPONENT-EXTENT-XML-ROOT");
            String suiteName = required(root, "name");
            int expectedTests = nonnegative(root, "tests"), expectedFailures = nonnegative(root, "failures");
            int expectedErrors = nonnegative(root, "errors"), expectedSkipped = nonnegative(root, "skipped");
            List<Case> cases = new ArrayList<>(); int failures = 0, errors = 0, skipped = 0;
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (!(node instanceof Element element) || !element.getTagName().equals("testcase")) continue;
                String className = required(element, "classname"), name = required(element, "name");
                Element failure = child(element, "failure"), error = child(element, "error"), skip = child(element, "skipped");
                if ((failure != null ? 1 : 0) + (error != null ? 1 : 0) + (skip != null ? 1 : 0) > 1)
                    throw new IOException("COMPONENT-EXTENT-TESTCASE-STATUS");
                String outcome = failure != null ? "FAIL" : error != null ? "ERROR" : skip != null ? "SKIP" : "PASS";
                Element detail = failure != null ? failure : error != null ? error : skip;
                String text = detail == null ? "" : safeText(detail.getAttribute("message") + " " + detail.getTextContent());
                cases.add(new Case(className, name, outcome, text));
                if (failure != null) failures++;
                if (error != null) errors++;
                if (skip != null) skipped++;
            }
            if (cases.size() != expectedTests || failures != expectedFailures || errors != expectedErrors || skipped != expectedSkipped)
                throw new IOException("COMPONENT-EXTENT-XML-COUNTS");
            return new ParsedSuite(suiteName, List.copyOf(cases), failures, errors, skipped);
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) { throw new IOException("COMPONENT-EXTENT-XML-PARSE", failure); }
    }

    private static Element child(Element parent, String name) throws IOException {
        Element found = null; NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) if (children.item(i) instanceof Element element && element.getTagName().equals(name)) {
            if (found != null) throw new IOException("COMPONENT-EXTENT-DUPLICATE-STATUS");
            found = element;
        }
        return found;
    }

    private static String required(Element element, String name) throws IOException {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) throw new IOException("COMPONENT-EXTENT-XML-ATTRIBUTE");
        return value;
    }

    private static int nonnegative(Element element, String name) throws IOException {
        String value = required(element, name);
        try { int parsed = Integer.parseInt(value); if (parsed < 0) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException failure) { throw new IOException("COMPONENT-EXTENT-XML-COUNT"); }
    }

    private static int exactInt(JsonNode node) throws IOException {
        if (node == null || !node.isNumber()) throw new IOException("COMPONENT-EXTENT-INPUT-COUNT");
        try { int value = node.decimalValue().intValueExact(); if (value < 0) throw new ArithmeticException(); return value; }
        catch (ArithmeticException failure) { throw new IOException("COMPONENT-EXTENT-INPUT-COUNT"); }
    }

    private static String safeText(String value) {
        String sanitized = value.replaceAll("(?i)(password|passwd|secret|token|api[_-]?key|access[_-]?token)\\s*[:=]\\s*\\S+", "$1=[REDACTED]")
                .replaceAll("\\s+", " ").trim();
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500) + "…";
    }

    private static String sha256(byte[] bytes) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IOException("SHA-256 unavailable", failure); }
    }
}

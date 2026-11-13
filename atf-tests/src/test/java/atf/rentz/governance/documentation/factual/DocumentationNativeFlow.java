package atf.rentz.governance.documentation.factual;

import atf.rentz.governance.documentation.DocumentationGovernanceSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class DocumentationNativeFlow {
    private static final String RUNNER = "atf-tests/tools/run-shared-ui-sanity.ps1";
    private static final String RAG = "atf-tests/src/main/java/atf/rentz/distribution/evidence/NativeUiEvidence.java";
    private static final List<String> GUIDES = List.of("README.md", "atf-tests/README.md",
            "docs/rentz/ATF_Android.md", "docs/rentz/ATF_Release.md", "docs/rentz/ATF_TestingStrategy.md");
    private static final Pattern COUNT = Pattern.compile("(?m)^\\h*\\$consecutiveGameCount\\h*=\\h*([1-9][0-9]*)\\h*$");
    private static final Pattern LOOP = Pattern.compile("(?m)^\\h*for\\(\\$deal=1;\\$deal-le\\$consecutiveGameCount;\\$deal\\+\\+\\)\\{");
    private static final Pattern FIRST = Pattern.compile("(?m)^\\h*if\\(\\$deal-eq1-and\\$contract-ne'([A-Z_]+)'\\)\\{throw ");
    private static final Pattern RAG_COUNT = Pattern.compile("(?m)^\\h*public static final int REQUIRED_COMPLETE_GAMES = ([1-9][0-9]*);\\h*$");
    private static final Pattern RAG_FIRST = Pattern.compile("contracts\\.get\\(0\\)\\.asText\\(\\)\\.equals\\(\"([A-Z_]+)\"\\)");
    private static final String LABEL = "**Traseul Android de release.** ";
    private static final Pattern CLAIM = Pattern.compile("(?:- )?" + Pattern.quote(LABEL)
            + "([1-9][0-9]*) mâini complete, cu ([\\p{L}_]+) prima\\.");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");

    private DocumentationNativeFlow() { }

    static List<DocumentationGovernanceSupport.Violation> inspect(Path root) throws IOException {
        var issues = new ArrayList<DocumentationGovernanceSupport.Violation>();
        String runner = Files.readString(root.resolve(RUNNER));
        String rag = Files.readString(root.resolve(RAG));
        String count = uniqueCapture(COUNT, runner);
        String first = uniqueCapture(FIRST, runner);
        if (count == null || first == null || !LOOP.matcher(runner).find()) {
            add(issues, root, RUNNER, 1, "DOC-NATIVE-RUNNER", "Consecutive flow is not identifiable",
                    "Restore one count, its loop, and an explicit first-contract check");
            return issues;
        }
        if (!count.equals(uniqueCapture(RAG_COUNT, rag)) || !first.equals(uniqueCapture(RAG_FIRST, rag))) {
            add(issues, root, RAG, 1, "DOC-NATIVE-RAG", "Runner requires " + count + " hands starting with " + first,
                    "Align the RAG count and first-contract check with the runner");
        }
        for (String guide : GUIDES) inspectGuide(root, guide, count, first, issues);
        return List.copyOf(issues);
    }

    private static String uniqueCapture(Pattern pattern, String text) {
        var match = pattern.matcher(text);
        if (!match.find()) return null;
        String value = match.group(1);
        return match.find() ? null : value;
    }

    private static void inspectGuide(Path root, String guide, String count, String first,
                                     List<DocumentationGovernanceSupport.Violation> issues) throws IOException {
        Path file = root.resolve(guide);
        List<String> lines = Files.isRegularFile(file) ? Files.readAllLines(file) : List.of();
        String fence = "";
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            var delimiter = FENCE.matcher(line);
            if (delimiter.matches()) {
                String run = delimiter.group(1);
                if (fence.isEmpty()) fence = run;
                else if (run.charAt(0) == fence.charAt(0) && run.length() >= fence.length()
                        && delimiter.group(2).isBlank()) fence = "";
                continue;
            }
            if (!fence.isEmpty() || !(line.startsWith(LABEL) || line.startsWith("- " + LABEL))) continue;
            var claim = CLAIM.matcher(line);
            if (found || !claim.matches() || !count.equals(claim.group(1)) || !first.equalsIgnoreCase(claim.group(2))) {
                add(issues, root, guide, i + 1, "DOC-NATIVE-GUIDE", line, expected(count, first));
            }
            found = true;
        }
        if (!found) add(issues, root, guide, 1, "DOC-NATIVE-GUIDE", "Release flow claim is missing", expected(count, first));
    }

    private static String expected(String count, String first) {
        return "Keep one visible 'Traseul Android de release' statement with " + count + " hands starting with " + first;
    }

    private static void add(List<DocumentationGovernanceSupport.Violation> issues, Path root, String file,
                            int line, String code, String finding, String fix) {
        issues.add(DocumentationGovernanceSupport.violation(code, root, root.resolve(file), line, finding,
                "The documented Android release flow must match the runner and the RAG gate", fix));
    }
}

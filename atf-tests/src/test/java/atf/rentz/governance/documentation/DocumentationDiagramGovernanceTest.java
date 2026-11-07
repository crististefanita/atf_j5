package atf.rentz.governance.documentation;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static atf.rentz.governance.source.GovernanceProject.locateProjectRoot;

/** Checks the stable visual contract of Mermaid diagrams embedded in README files. */
@AtfComponentTest
public class DocumentationDiagramGovernanceTest {
    private static final int ROOT_MODULE_MAP_MAX_NODE_LABEL_CHARS = 18;
    private static final int ROOT_MODULE_MAP_MAX_EDGE_LABEL_CHARS = 12;
    private static final Pattern NODE_LABEL = Pattern.compile("^\\w+\\[(.+)]$");
    private static final Pattern EDGE_LABEL = Pattern.compile("(?:-->|-\\.->)\\|([^|]+)\\|");

    @Test
    void nonRootReadmeDiagramsKeepTheDocumentedVisualContract() throws Exception {
        Path root = locateProjectRoot();
        List<DocumentationGovernanceSupport.Violation> violations = new java.util.ArrayList<>();
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> DocumentationGovernanceSupport.isGovernedMarkdown(root, path))
                    .forEach(path -> validate(path, root, violations));
        }

        DocumentationGovernanceSupport.assertNoViolations("README diagram governance", violations);
    }

    static void validate(Path path, Path root,
                         List<DocumentationGovernanceSupport.Violation> violations) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (int index = 0; index < lines.size(); index++) {
                if (!lines.get(index).trim().equals("```mermaid")) continue;

                int closing = findClosingFence(lines, index + 1);
                if (closing < 0) {
                    violations.add(DocumentationGovernanceSupport.violation(
                            "DOC-DIAG-FENCE", root, path, index + 1,
                            "Mermaid fence is not closed",
                            "Every Mermaid block must have a closing ``` fence",
                            "close the diagram before the next Markdown section"));
                    break;
                }

                inspectDiagram(lines, index, closing, root, path, violations);
                inspectImmediateLegend(lines, closing, root, path, violations);
                index = closing;
            }
        } catch (Exception error) {
            violations.add(DocumentationGovernanceSupport.violation(
                    "DOC-READ", root, path, 1,
                    "README could not be read: " + error.getMessage(),
                    "every owner README must be readable by the governance scan",
                    "fix the file encoding or filesystem access before changing content"));
        }
    }

    private static int findClosingFence(List<String> lines, int start) {
        for (int index = start; index < lines.size(); index++) {
            if (lines.get(index).trim().equals("```")) return index;
        }
        return -1;
    }

    private static void inspectDiagram(List<String> lines, int opening, int closing,
                                       Path root, Path path,
                                       List<DocumentationGovernanceSupport.Violation> violations) {
        String firstStatement = firstExecutableStatement(lines, opening + 1, closing);
        if (firstStatement == null || !firstStatement.matches("(?:flowchart\\s+LR|sequenceDiagram|stateDiagram-v2|classDiagram).*")) {
            violations.add(DocumentationGovernanceSupport.violation(
                    "DOC-DIAG-SYNTAX", root, path, opening + 1,
                    "Mermaid diagram has no supported type",
                    "start with flowchart LR, sequenceDiagram, stateDiagram-v2 or classDiagram",
                    "put the Mermaid diagram type on the first executable line"));
            return;
        }
        if (!firstStatement.startsWith("flowchart")) return;
        if (!firstStatement.matches("flowchart\\s+LR.*")) {
            violations.add(DocumentationGovernanceSupport.violation(
                    "DOC-DIAG-ORIENTATION", root, path, opening + 2,
                    "flowchart uses a non-approved orientation",
                    "use flowchart LR for non-root README diagrams",
                    "reorder the nodes left-to-right; keep TB only when the approved root baseline requires it"));
        }

        int edges = 0;
        List<String> unlabeledEdges = new java.util.ArrayList<>();
        for (int index = opening + 2; index < closing; index++) {
            String line = lines.get(index).trim();
            if (isRelationshipLine(line)) {
                edges++;
                if (!line.contains("-->|")
                        && !line.contains("-.->|")
                        && !line.matches(".*-\\.\\s+.+\\s+\\.->.*")) {
                    unlabeledEdges.add(line);
                }
            }
        }
        if (edges >= 2 && !unlabeledEdges.isEmpty()) {
            violations.add(DocumentationGovernanceSupport.violation(
                    "DOC-DIAG-EDGE", root, path, opening + 1,
                    unlabeledEdges.size() + " flowchart relationship(s) have no semantic edge label; examples: "
                            + unlabeledEdges.stream().limit(2).toList(),
                    "ownership, consumption and flow relations use one short verb on every edge",
                    "change `A --> B` to `A -->|consumă| B` or use an equivalent concise verb"));
        }

        if (isRootModuleMap(path, root, lines, opening)) {
            inspectRootModuleMapCompactness(lines, opening, closing, root, path, violations);
        }
    }

    private static boolean isRootModuleMap(Path path, Path root, List<String> lines, int opening) {
        if (!path.normalize().equals(root.resolve("README.md").normalize())) return false;
        for (int index = opening - 1; index >= 0; index--) {
            String line = lines.get(index).trim();
            if (line.startsWith("## ")) return line.equals("## Harta modulelor");
        }
        return false;
    }

    private static void inspectRootModuleMapCompactness(List<String> lines, int opening, int closing,
                                                        Path root, Path path,
                                                        List<DocumentationGovernanceSupport.Violation> violations) {
        for (int index = opening + 1; index < closing; index++) {
            String line = lines.get(index).trim();
            Matcher node = NODE_LABEL.matcher(line);
            if (node.matches()) {
                String label = node.group(1).replaceAll("<br\\s*/?>", " ")
                        .replace("\"", "").trim();
                if (label.length() > ROOT_MODULE_MAP_MAX_NODE_LABEL_CHARS || node.group(1).contains("<br")) {
                    violations.add(DocumentationGovernanceSupport.violation(
                            "DOC-DIAG-COMPACT", root, path, index + 1,
                            "root module-map node label is too large: " + node.group(1),
                            "module-map nodes use one short concept (max "
                                    + ROOT_MODULE_MAP_MAX_NODE_LABEL_CHARS + " visible characters, no forced line break)",
                            "shorten the node label and move the full module name to the collapsed legend/table"));
                }
            }

            Matcher edge = EDGE_LABEL.matcher(line);
            if (edge.find() && edge.group(1).trim().length() > ROOT_MODULE_MAP_MAX_EDGE_LABEL_CHARS) {
                violations.add(DocumentationGovernanceSupport.violation(
                        "DOC-DIAG-COMPACT", root, path, index + 1,
                        "root module-map edge label is too large: " + edge.group(1).trim(),
                        "edge labels use one short verb (max " + ROOT_MODULE_MAP_MAX_EDGE_LABEL_CHARS + " characters)",
                        "replace the phrase with a short verb and move the explanation to the collapsed legend"));
            }
        }
    }

    private static boolean isRelationshipLine(String line) {
        return line.contains("-->") || line.matches(".*\\.->.*");
    }

    private static void inspectImmediateLegend(List<String> lines, int closing,
                                               Path root, Path path,
                                               List<DocumentationGovernanceSupport.Violation> violations) {
        int next = closing + 1;
        while (next < lines.size() && lines.get(next).isBlank()) next++;
        if (next >= lines.size() || !lines.get(next).trim().equals("<details>")) {
            violations.add(DocumentationGovernanceSupport.violation(
                    "DOC-DIAG-LEGEND", root, path, closing + 1,
                    "diagram is not followed by one collapsed legend",
                    "the next non-empty block must be <details> with a Legendă summary",
                    "move the diagram legend directly below the closing Mermaid fence"));
            return;
        }
        int summaryEnd = Math.min(lines.size(), next + 4);
        boolean hasLegendSummary = false;
        for (int index = next; index < summaryEnd; index++) {
            if (lines.get(index).toLowerCase().contains("legend")) {
                hasLegendSummary = true;
                break;
            }
        }
        if (!hasLegendSummary) {
            violations.add(DocumentationGovernanceSupport.violation(
                    "DOC-DIAG-LEGEND", root, path, next + 1,
                    "collapsed details does not identify a diagram legend",
                    "the summary must say Legendă and explain the diagram boundary",
                    "rename the summary and keep secondary tables inside the same details block"));
        }
    }

    private static String firstExecutableStatement(List<String> lines, int start, int end) {
        for (int index = start; index < end; index++) {
            String line = lines.get(index).trim();
            if (line.isBlank() || line.startsWith("%%")) continue;
            return line;
        }
        return null;
    }

}

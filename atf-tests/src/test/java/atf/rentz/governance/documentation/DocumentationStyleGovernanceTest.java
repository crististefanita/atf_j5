package atf.rentz.governance.documentation;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static atf.rentz.governance.source.GovernanceProject.locateProjectRoot;

/** Protects the renderer-neutral ATF README visual language. */
@AtfComponentTest
public class DocumentationStyleGovernanceTest {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+.+$");
    private static final Pattern FENCE = Pattern.compile("^\\s*```(.*)$");
    private static final Pattern PRESENTATIONAL_HTML = Pattern.compile(
            "(?i)<(?:font|h[1-6]|span|div)\\b|\\bstyle\\s*=");
    private static final Pattern NON_CANONICAL_BULLET = Pattern.compile("^\\s*[+*]\\s+.+$");
    private static final Pattern LANGUAGE = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");

    @Test
    void everyOwnerReadmeUsesTheRendererNeutralAtfStyle() throws Exception {
        Path root = locateProjectRoot();
        List<DocumentationGovernanceSupport.Violation> violations = new ArrayList<>();
        try (var files = Files.walk(root)) {
            files.filter(path -> DocumentationGovernanceSupport.isGovernedMarkdown(root, path))
                    .forEach(path -> validate(path, root, violations));
        }
        DocumentationGovernanceSupport.assertNoViolations("README ATF style governance", violations);
    }

    static void validate(Path path, Path root,
                         List<DocumentationGovernanceSupport.Violation> violations) {
        try {
            List<String> lines = Files.readAllLines(path);
            boolean inFence = false;
            int previousHeadingLevel = 1;
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                MatcherState state = fenceState(line);
                if (inFence) {
                    if (state.closing()) inFence = false;
                    continue;
                }
                if (state.opening()) {
                    inFence = true;
                    if (state.language().isBlank()) {
                        violations.add(DocumentationGovernanceSupport.violation(
                                "DOC-STYLE-FENCE", root, path, index + 1,
                                "code fence has no language identifier",
                                "every executable or schematic block uses a typed Markdown fence",
                                "write ` ```text `, ` ```mermaid `, ` ```java ` or the appropriate language"));
                    } else if (!LANGUAGE.matcher(state.language()).matches()) {
                        violations.add(DocumentationGovernanceSupport.violation(
                                "DOC-STYLE-FENCE", root, path, index + 1,
                                "code fence language is not a simple renderer token: " + state.language(),
                                "fence labels must be portable across Markdown renderers",
                                "use one identifier such as `mermaid`, `text`, `java`, `xml` or `yaml`"));
                    }
                    continue;
                }
                if (PRESENTATIONAL_HTML.matcher(line).find()) {
                    violations.add(DocumentationGovernanceSupport.violation(
                            "DOC-STYLE-PRESENTATION", root, path, index + 1,
                            "README contains renderer-specific HTML/CSS presentation",
                            "ATF style is semantic Markdown; the host renderer owns font, size and color",
                            "replace font/style tags with headings, bullets, tables, code fences or `<details>`"));
                }
                if (NON_CANONICAL_BULLET.matcher(line).matches()) {
                    violations.add(DocumentationGovernanceSupport.violation(
                            "DOC-STYLE-BULLET", root, path, index + 1,
                            "README uses `*` or `+` as a list marker",
                            "ATF prose uses `-` bullets for one stable scan pattern",
                            "replace the marker with `-` and keep the item concise"));
                }
                var heading = HEADING.matcher(line);
                if (heading.matches()) {
                    int level = heading.group(1).length();
                    if (level > previousHeadingLevel + 1) {
                        violations.add(DocumentationGovernanceSupport.violation(
                                "DOC-STYLE-HEADING", root, path, index + 1,
                                "heading jumps from level " + previousHeadingLevel + " to level " + level,
                                "the outline must remain scannable and hierarchical",
                                "insert the missing intermediate heading or lower this heading one level"));
                    }
                    previousHeadingLevel = level;
                }
            }
            if (inFence) {
                violations.add(DocumentationGovernanceSupport.violation(
                        "DOC-STYLE-FENCE", root, path, lines.size(),
                        "code fence is not closed",
                        "every fenced block must have a matching closing fence",
                        "close the block before the next Markdown section"));
            }
        } catch (Exception error) {
            violations.add(DocumentationGovernanceSupport.violation(
                    "DOC-READ", root, path, 1,
                    "README could not be read: " + error.getMessage(),
                    "every owner README must be readable by the governance scan",
                    "fix the file encoding or filesystem access before changing content"));
        }
    }

    private static MatcherState fenceState(String line) {
        var matcher = FENCE.matcher(line);
        if (!matcher.matches()) return new MatcherState(false, false, "");
        String suffix = matcher.group(1).trim();
        return new MatcherState(true, suffix.isBlank(), suffix);
    }

    private record MatcherState(boolean opening, boolean closing, String language) {
    }
}

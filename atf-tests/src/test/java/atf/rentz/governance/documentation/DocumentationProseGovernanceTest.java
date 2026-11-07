package atf.rentz.governance.documentation;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static atf.rentz.governance.source.GovernanceProject.locateProjectRoot;

/** Keeps rapid-scan README prose schematic instead of paragraph-heavy. */
@AtfComponentTest
public class DocumentationProseGovernanceTest {
    /** Calibration knob: raise only when the documentation rule is deliberately changed. */
    private static final int MAX_SENTENCES_PER_PARAGRAPH = 3;
    private static final int MIN_SENTENCES_FOR_CONSECUTIVE_RUN = 2;
    private static final int MAX_CONSECUTIVE_DENSE_PARAGRAPHS = 2;
    private static final int MAX_PARAGRAPH_PREVIEW_LENGTH = 160;

    private static final Pattern FENCE = Pattern.compile("^\\s*```.*$");
    private static final Pattern HEADING = Pattern.compile("^\\s*#{1,6}\\s+.+$");
    private static final Pattern LIST = Pattern.compile("^\\s*(?:[-+*]|\\d+[.)])\\s+.+$");
    private static final Pattern TABLE = Pattern.compile("^\\s*\\|.*$");
    private static final Pattern BLOCKQUOTE = Pattern.compile("^\\s*>.*$");
    private static final Pattern DETAILS_OPEN = Pattern.compile("(?i)<details\\b");
    private static final Pattern DETAILS_CLOSE = Pattern.compile("(?i)</details\\s*>");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])(?=\\s|$)");

    @Test
    void ownerReadmesKeepRapidScanProseWithinTheDocumentedLimit() throws Exception {
        Path root = locateProjectRoot();
        List<DocumentationGovernanceSupport.Violation> violations = new ArrayList<>();
        try (var files = Files.walk(root)) {
            files.filter(path -> DocumentationGovernanceSupport.isGovernedMarkdown(root, path))
                    .forEach(path -> validate(path, root, violations));
        }
        DocumentationGovernanceSupport.assertNoViolations(
                "README prose density governance", violations);
    }

    static void validate(Path path, Path root,
                         List<DocumentationGovernanceSupport.Violation> violations) {
        try {
            List<String> lines = Files.readAllLines(path);
            List<Paragraph> paragraphs = new ArrayList<>();
            StringBuilder paragraph = new StringBuilder();
            int paragraphStart = -1;
            int section = 0;
            boolean inFence = false;
            int detailsDepth = 0;
            boolean listContinuation = false;

            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (FENCE.matcher(line).matches()) {
                    addParagraph(paragraphs, paragraph, paragraphStart, section);
                    paragraph.setLength(0);
                    paragraphStart = -1;
                    inFence = !inFence;
                    section++;
                    continue;
                }
                if (inFence) continue;

                if (DETAILS_OPEN.matcher(line).find()) {
                    addParagraph(paragraphs, paragraph, paragraphStart, section);
                    paragraph.setLength(0);
                    paragraphStart = -1;
                    detailsDepth++;
                    section++;
                    continue;
                }
                if (DETAILS_CLOSE.matcher(line).find()) {
                    detailsDepth = Math.max(0, detailsDepth - 1);
                    section++;
                    continue;
                }
                if (detailsDepth > 0) {
                    addParagraph(paragraphs, paragraph, paragraphStart, section);
                    paragraph.setLength(0);
                    paragraphStart = -1;
                    section++;
                    continue;
                }
                if (line.isBlank()) {
                    listContinuation = false;
                    addParagraph(paragraphs, paragraph, paragraphStart, section);
                    paragraph.setLength(0);
                    paragraphStart = -1;
                    continue;
                }
                if (listContinuation && isIndentedContinuation(line)) continue;
                if (isStructural(line)) {
                    listContinuation = LIST.matcher(line).matches();
                    addParagraph(paragraphs, paragraph, paragraphStart, section);
                    paragraph.setLength(0);
                    paragraphStart = -1;
                    section++;
                    continue;
                }
                listContinuation = false;

                if (paragraphStart < 0) paragraphStart = index + 1;
                if (paragraph.length() > 0) paragraph.append(' ');
                paragraph.append(line.trim());
            }
            addParagraph(paragraphs, paragraph, paragraphStart, section);
            inspectParagraphs(paragraphs, path, root, violations);
        } catch (Exception error) {
            violations.add(DocumentationGovernanceSupport.violation(
                    "DOC-READ", root, path, 1,
                    "README could not be read: " + error.getMessage(),
                    "every owner README must be readable by the governance scan",
                    "fix the file encoding or filesystem access before changing content"));
        }
    }

    private static void addParagraph(List<Paragraph> paragraphs, StringBuilder paragraph,
                                     int startLine, int section) {
        if (!paragraph.isEmpty() && startLine >= 0) {
            paragraphs.add(new Paragraph(startLine, paragraph.toString(), section));
        }
    }

    private static void inspectParagraphs(List<Paragraph> paragraphs, Path path, Path root,
                                         List<DocumentationGovernanceSupport.Violation> violations) {
        int previousSection = -1;
        int denseRun = 0;
        for (Paragraph paragraph : paragraphs) {
            if (paragraph.section() != previousSection) {
                denseRun = 0;
                previousSection = paragraph.section();
            }

            int sentenceCount = countSentences(paragraph.text());
            if (sentenceCount > MAX_SENTENCES_PER_PARAGRAPH) {
                violations.add(DocumentationGovernanceSupport.violation(
                        "DOC-PROSE-DENSE", root, path, paragraph.startLine(),
                        sentenceCount + " consecutive sentences in one rapid-scan paragraph: "
                                + preview(paragraph.text()),
                        "rapid-scan prose has at most " + MAX_SENTENCES_PER_PARAGRAPH
                                + " sentences; responsibilities, limits and relationships use bullets, tables or diagrams",
                        "split the block into `-` bullets, a compact table or a diagram; move rationale and long examples into `<details>`"));
            }

            if (sentenceCount >= MIN_SENTENCES_FOR_CONSECUTIVE_RUN) {
                denseRun++;
                if (denseRun > MAX_CONSECUTIVE_DENSE_PARAGRAPHS) {
                    violations.add(DocumentationGovernanceSupport.violation(
                            "DOC-PROSE-RUN", root, path, paragraph.startLine(),
                            denseRun + " consecutive dense prose paragraphs",
                            "rapid-scan sections use one short framing paragraph, then schematic content",
                            "replace the run with bullets, a table or a diagram; keep only the scope/conclusion paragraph visible"));
                }
            } else {
                denseRun = 0;
            }
        }
    }

    private static int countSentences(String text) {
        return (int) SENTENCE_END.matcher(text).results().count();
    }

    private static String preview(String text) {
        if (text.length() <= MAX_PARAGRAPH_PREVIEW_LENGTH) return text;
        return text.substring(0, MAX_PARAGRAPH_PREVIEW_LENGTH - 1) + "…";
    }

    private static boolean isStructural(String line) {
        return HEADING.matcher(line).matches()
                || LIST.matcher(line).matches()
                || TABLE.matcher(line).matches()
                || BLOCKQUOTE.matcher(line).matches()
                || line.trim().equals("---");
    }

    private static boolean isIndentedContinuation(String line) {
        return line.startsWith("  ") || line.startsWith("\t");
    }

    private record Paragraph(int startLine, String text, int section) {
    }
}

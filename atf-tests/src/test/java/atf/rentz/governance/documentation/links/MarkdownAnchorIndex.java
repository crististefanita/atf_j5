package atf.rentz.governance.documentation.links;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Heading/explicit-anchor index for the repository's Markdown; fenced examples are not targets. */
public final class MarkdownAnchorIndex {
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern HEADING = Pattern.compile("^ {0,3}#{1,6}[ \\t]+(.+?)\\s*$");
    private static final Pattern SETEXT = Pattern.compile("^ {0,3}(?:=+|-+)[ \\t]*$");
    private static final Pattern TAG = Pattern.compile("(?i)<([a-z][\\w:-]*)\\b([^>]*)>");
    private static final Pattern ATTRIBUTE = Pattern.compile("(?i)(?:^|\\s)(id|name)\\s*=\\s*([\"'])(.*?)\\2");

    private MarkdownAnchorIndex() { }

    /** Retains original line positions so every link diagnostic still points to its source line. */
    public static List<String> withoutFences(List<String> lines) {
        var result = new ArrayList<String>(lines.size());
        char marker = 0;
        int length = 0;
        for (String line : lines) {
            var fence = FENCE.matcher(line);
            if (marker == 0) {
                if (fence.matches()) {
                    marker = fence.group(1).charAt(0);
                    length = fence.group(1).length();
                    result.add("");
                } else result.add(line);
            } else {
                result.add("");
                if (fence.matches() && fence.group(1).charAt(0) == marker
                        && fence.group(1).length() >= length && fence.group(2).isBlank()) marker = 0;
            }
        }
        return List.copyOf(result);
    }

    public static Set<String> anchors(List<String> lines) {
        var visible = withoutFences(lines);
        var headings = new HashSet<String>();
        var explicit = new HashSet<String>();
        for (int index = 0; index < visible.size(); index++) {
            String line = visible.get(index);
            var heading = HEADING.matcher(line);
            if (heading.matches()) {
                addHeading(headings, heading.group(1).replaceFirst("[ \\t]+#+[ \\t]*$", ""));
            } else if (SETEXT.matcher(line).matches() && index > 0) {
                String previous = visible.get(index - 1);
                if (!previous.isBlank() && !HEADING.matcher(previous).matches()
                        && !SETEXT.matcher(previous).matches()) addHeading(headings, previous.strip());
            }
            var tag = TAG.matcher(line.replaceAll("`[^`]*`", ""));
            while (tag.find()) {
                var attribute = ATTRIBUTE.matcher(tag.group(2));
                while (attribute.find()) {
                    if (attribute.group(1).equalsIgnoreCase("id") || tag.group(1).equalsIgnoreCase("a"))
                        explicit.add(attribute.group(3));
                }
            }
        }
        headings.addAll(explicit);
        return Set.copyOf(headings);
    }

    private static void addHeading(Set<String> headings, String text) {
        String base = text.replaceAll("!?\\[([^]\\n]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?<!\\w)_{1,2}(.+?)_{1,2}(?!\\w)", "$1")
                .replaceAll("<[^>]+>", "").replaceAll("[*`~]", "")
                .strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\p{M}_ -]", "").replace(' ', '-');
        String anchor = base;
        for (int suffix = 1; !headings.add(anchor); suffix++) anchor = base + "-" + suffix;
    }
}

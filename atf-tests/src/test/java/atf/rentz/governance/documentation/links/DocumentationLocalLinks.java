package atf.rentz.governance.documentation.links;

import atf.rentz.governance.documentation.DocumentationGovernanceSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One local-link parser shared by README structure, the docs scan and explicit targets. */
public final class DocumentationLocalLinks {
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]+\\]\\(([^)]+)\\)");
    private DocumentationLocalLinks() { }

    public static void validate(Path path, Path root, List<String> lines,
                                           List<DocumentationGovernanceSupport.Violation> violations) {
        lines = MarkdownAnchorIndex.withoutFences(lines);
        for (int index = 0; index < lines.size(); index++) {
            Matcher matcher = MARKDOWN_LINK.matcher(lines.get(index));
            while (matcher.find()) {
                if (matcher.start() > 0 && lines.get(index).charAt(matcher.start() - 1) == '!') continue;
                String destination = matcher.group(1).trim();
                String target = destination.replaceFirst("^<", "").replaceFirst(">$", "");
                if (isExternalOrConfigured(target)) continue;
                int fragment = target.indexOf('#');
                String anchor = fragment < 0 ? "" : target.substring(fragment + 1);
                String file = fragment < 0 ? target : target.substring(0, fragment);
                Path resolved;
                try {
                    resolved = file.isBlank() ? path : path.getParent().resolve(decode(file)).normalize();
                    Path stage = root.toAbsolutePath().normalize();
                    String sourceRoot = System.getProperty("rentz.governance.sourceRoot");
                    if (!resolved.toAbsolutePath().normalize().startsWith(stage) && sourceRoot != null
                            && Files.isRegularFile(stage.resolve("config/java-source-layout.json"))) {
                        Path source = Path.of(sourceRoot).toAbsolutePath().normalize();
                        Path originalDocument = source.resolve(stage.relativize(path.toAbsolutePath().normalize()));
                        if (Files.mismatch(path, originalDocument) != -1) {
                            throw new IllegalArgumentException("Document differs from its explicit source root");
                        }
                        String checkoutLayout = System.getProperty("rentz.governance.checkoutLayout", sourceRoot);
                        Path origin = Path.of(checkoutLayout).resolve(stage.relativize(path.toAbsolutePath().normalize()));
                        resolved = origin.getParent().resolve(decode(file)).normalize();
                    }
                    anchor = decode(anchor);
                } catch (IllegalArgumentException | java.io.IOException invalid) {
                    violations.add(DocumentationGovernanceSupport.violation(
                            "DOC-README-LINK", root, path, index + 1,
                            "local link has an invalid path or percent escape: " + destination,
                            "local link destinations must be valid URI paths and fragments",
                            "correct the path or encode the literal percent sign as %25"));
                    continue;
                }
                if (!Files.exists(resolved)) {
                    violations.add(DocumentationGovernanceSupport.violation(
                            "DOC-README-LINK", root, path, index + 1,
                            "local link points to missing target: " + destination,
                            "owner README links must resolve from the file that declares them",
                            "correct the relative path, or remove the link if the target is no longer an owner"));
                } else if (!anchor.isEmpty() && resolved.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
                    try {
                        if (!MarkdownAnchorIndex.anchors(Files.readAllLines(resolved)).contains(anchor)) {
                            violations.add(DocumentationGovernanceSupport.violation(
                                    "DOC-README-ANCHOR", root, path, index + 1,
                                    "local link points to missing Markdown fragment: " + destination,
                                    "the destination heading or explicit HTML anchor must exist outside fenced examples",
                                    "correct the fragment, restore the destination heading, or add its stable HTML anchor"));
                        }
                    } catch (java.io.IOException unreadable) {
                        violations.add(DocumentationGovernanceSupport.violation(
                                "DOC-README-ANCHOR", root, path, index + 1,
                                "Markdown link target could not be read: " + destination,
                                "fragment validation requires a readable Markdown destination",
                                "fix the target encoding or filesystem access"));
                    }
                }
            }
        }
    }

    private static String decode(String text) {
        return java.net.URLDecoder.decode(text.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean isExternalOrConfigured(String destination) {
        String normalized = destination.toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("mailto:")
                || normalized.startsWith("file:")
                || destination.contains("${")
                || destination.contains("<configured");
    }
}

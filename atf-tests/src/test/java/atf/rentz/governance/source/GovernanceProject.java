package atf.rentz.governance.source;

import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit root for staged validation; no fallback to a neighboring consumer. */
public final class GovernanceProject {
    private GovernanceProject() { }
    public static Path locateProjectRoot() { return root(); }
    public static Path root() {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (root != null && !Files.isRegularFile(root.resolve("config/java-source-layout.json"))) {
            root = root.getParent();
        }
        if (root == null) throw new IllegalStateException("GOV-ROOT: declared source layout absent");
        return root;
    }
}

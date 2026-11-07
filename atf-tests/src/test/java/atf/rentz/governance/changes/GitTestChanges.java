package atf.rentz.governance.changes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Uses Git's NUL-delimited protocol, including staged, unstaged, committed and untracked edits. */
final class GitTestChanges {
    static final String BASE_PROPERTY = "atf.governance.testChanges.baseRef";
    static final String BASE_ENV = "ATF_TEST_CHANGE_BASE_REF";
    private GitTestChanges() { }

    record Change(String kind, String path) { }
    record Snapshot(String base, List<Change> changes) {
        Snapshot { changes = List.copyOf(changes); }
    }

    static String baseRef(String property, Map<String, String> environment) {
        String base = property == null || property.isBlank() ? environment.get(BASE_ENV) : property;
        boolean ci = environment.containsKey("CI") && !"false".equalsIgnoreCase(environment.get("CI"));
        if (base == null || base.isBlank()) {
            if (ci) throw new IllegalStateException("TEST-CHANGE-BASE: CI requires " + BASE_PROPERTY + " or " + BASE_ENV);
            return "HEAD";
        }
        if (ci && "HEAD".equalsIgnoreCase(base.trim())) {
            throw new IllegalStateException("TEST-CHANGE-BASE: CI must compare against the review base, not HEAD");
        }
        return base.trim();
    }

    static Snapshot read(Path root, String ref) throws Exception {
        String base = command(root, "rev-parse", "--verify", "--end-of-options", ref + "^{commit}").trim();
        if (!base.matches("[a-f0-9]{40}|[a-f0-9]{64}")) {
            throw new IllegalStateException("TEST-CHANGE-BASE: invalid resolved commit");
        }
        command(root, "merge-base", "--is-ancestor", base, "HEAD");
        var changes = new LinkedHashSet<Change>();
        changes.addAll(trackedChanges(root, base));
        changes.addAll(trackedChanges(root, base, "--cached"));
        for (String path : command(root, "ls-files", "--others", "--exclude-standard", "-z", "--", ".")
                .split("\u0000")) {
            if (!path.isEmpty()) changes.add(new Change("A", path));
        }
        for (String path : command(root, "ls-files", "--others", "--ignored", "--exclude-standard", "-z",
                "--", "pom.xml", ":(glob)**/pom.xml", ":(glob)**/src/test/**").split("\u0000")) {
            if (!path.isEmpty()) changes.add(new Change("A", path));
        }
        return new Snapshot(base, List.copyOf(changes));
    }

    private static List<Change> trackedChanges(Path root, String base, String... options) throws Exception {
        var args = new ArrayList<>(List.of("diff", "--no-ext-diff", "--no-textconv", "--relative",
                "--name-status", "-z", "--no-renames"));
        args.addAll(List.of(options));
        args.addAll(List.of(base, "--", "."));
        String[] fields = command(root, args.toArray(String[]::new)).split("\u0000", -1);
        var changes = new ArrayList<Change>();
        for (int i = 0; i < fields.length - 1; i += 2) {
            if (fields[i].isEmpty() || fields[i + 1].isEmpty()) {
                throw new IOException("TEST-CHANGE-GIT: invalid name-status protocol");
            }
            changes.add(new Change(fields[i], fields[i + 1]));
        }
        return List.copyOf(changes);
    }

    static String command(Path root, String... args) throws Exception {
        var invocation = new ArrayList<>(List.of("git", "-C", root.toAbsolutePath().toString()));
        invocation.addAll(List.of(args));
        Process process = new ProcessBuilder(invocation).start();
        var errors = CompletableFuture.runAsync(() -> {
            try {
                process.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream());
            } catch (IOException error) {
                throw new IllegalStateException("Cannot drain Git diagnostics", error);
            }
        });
        var output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException error) {
                throw new IllegalStateException("Cannot read Git output", error);
            }
        });
        try {
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                throw new IOException("TEST-CHANGE-GIT: Git exceeded its 20 second limit");
            }
            String result = output.get(5, TimeUnit.SECONDS);
            errors.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new IOException("TEST-CHANGE-GIT: Git failed for " + args[0]
                        + "; verify checkout and baseline (exit " + process.exitValue() + ")");
            }
            return result;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}

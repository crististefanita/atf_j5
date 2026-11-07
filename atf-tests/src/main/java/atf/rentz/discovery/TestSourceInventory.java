package atf.rentz.discovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/** One syntax inventory for source governance and collection selection; no compilation of the input. */
public final class TestSourceInventory {
    public record Entry(String file, String name, boolean executable, boolean concrete,
                        boolean nested, String kind, List<String> layers, List<TestLayers.ReportSuite> suites) { }
    private TestSourceInventory() { }

    public static List<Entry> scan(Path project, List<Path> roots) throws IOException {
        return scan(project, roots, java.util.Set.copyOf(roots), java.util.Set.of());
    }
    public static List<Entry> scan(Path project, List<Path> roots, java.util.Set<Path> testRoots,
                                   java.util.Set<String> externalNonTestBases) throws IOException {
        var files = new ArrayList<Path>();
        var checked = new java.util.HashSet<Path>();
        for (Path source : roots) {
            Path absolute = source.toAbsolutePath().normalize();
            if (!absolute.startsWith(project.toAbsolutePath().normalize()) || !Files.isDirectory(absolute)) {
                throw new IllegalArgumentException("TEST-INVENTORY: missing or escaped source root " + source);
            }
            Files.walkFileTree(absolute, new java.nio.file.SimpleFileVisitor<>() {
                @Override public java.nio.file.FileVisitResult preVisitDirectory(Path directory, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    if (Files.isSymbolicLink(directory) || !directory.toAbsolutePath().normalize().equals(directory.toRealPath())) {
                        throw new IOException("TEST-INVENTORY: linked source directory");
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                @Override public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    if (Files.isSymbolicLink(file) || !file.toAbsolutePath().normalize().equals(file.toRealPath())) throw new IOException("TEST-INVENTORY: linked source file");
                    if (file.toString().endsWith(".java")) {
                        files.add(file);
                        if (testRoots.stream().map(p -> p.toAbsolutePath().normalize()).anyMatch(absolute::equals)) checked.add(file);
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        }
        if (files.stream().distinct().count() != files.size()) throw new IllegalArgumentException("TEST-INVENTORY: overlapping source roots");
        var sources = new ArrayList<String>();
        for (Path file : files) sources.add(Files.readString(file));
        var hierarchy = new TestHierarchy(sources, java.util.Map.of(), externalNonTestBases);
        var result = new ArrayList<Entry>();
        for (int index = 0; index < files.size(); index++) {
            String source = sources.get(index);
            var unit = JavaTestDeclarations.parse(source);
            var inspections = hierarchy.inspect(index, checked.contains(files.get(index)));
            for (var inspection : inspections) {
                String top = inspection.name().split("\\.")[0];
                var type = unit.types().stream().filter(t -> t.name().equals(top)).findFirst().orElseThrow();
                String name = unit.packageName().isEmpty() ? inspection.name() : unit.packageName() + "." + inspection.name();
                result.add(new Entry(project.toAbsolutePath().normalize().relativize(files.get(index)).toString().replace('\\', '/'),
                        name, inspection.executable(), type.concrete(), inspection.name().contains("."),
                        type.kind().name(), inspection.layers(), inspection.suites()));
            }
        }
        result.sort(Comparator.comparing(Entry::file).thenComparing(Entry::name));
        return List.copyOf(result);
    }

    /** Versioned TSV bridge; source paths are Base64, so whitespace cannot alter row boundaries. */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("TEST-INVENTORY: project and declared roots required");
        Path project = Path.of(args[0]).toAbsolutePath().normalize();
        var roots = new ArrayList<Path>();
        var tests = new java.util.HashSet<Path>();
        var external = new java.util.HashSet<String>();
        for (int i = 1; i < args.length; i++) {
            int split = args[i].indexOf('=');
            if (split < 1) throw new IllegalArgumentException("TEST-INVENTORY: explicit source role required");
            String role = args[i].substring(0, split), value = args[i].substring(split + 1);
            if (role.equals("external")) {
                if (!value.matches("[A-Za-z_$][A-Za-z0-9_.$]*") || !external.add(value))
                    throw new IllegalArgumentException("TEST-INVENTORY: invalid reviewed external interface");
            } else {
                if (!java.util.Set.of("main", "test").contains(role)) throw new IllegalArgumentException("TEST-INVENTORY: unknown source role");
                Path root = project.resolve(value); roots.add(root);
                if (role.equals("test")) tests.add(root);
            }
        }
        if (roots.isEmpty()) throw new IllegalArgumentException("TEST-INVENTORY: declared source roots required");
        System.out.println("ATF-TEST-INVENTORY-2");
        for (var entry : scan(project, roots, tests, external)) {
            String file = Base64.getEncoder().encodeToString(entry.file().getBytes(StandardCharsets.UTF_8));
            System.out.println(String.join("\t", file, entry.name(), String.valueOf(entry.executable()),
                    String.valueOf(entry.concrete()), String.valueOf(entry.nested()), entry.kind(),
                    String.join(",", entry.layers()), String.join(",", entry.suites().stream().map(TestLayers.ReportSuite::binaryName).toList()),
                    String.join(",", entry.suites().stream().filter(TestLayers.ReportSuite::hasCases).map(TestLayers.ReportSuite::binaryName).toList())));
        }
    }
}

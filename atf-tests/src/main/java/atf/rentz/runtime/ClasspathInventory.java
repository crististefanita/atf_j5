package atf.rentz.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/** Captures every byte-bearing classpath entry in order, including classes, resources and JARs. */
public final class ClasspathInventory {
    public record FileStamp(String path, String sha256) { }
    public record Entry(String path, String kind, List<FileStamp> files) {
        public Entry { files = List.copyOf(files); }
    }
    public record Snapshot(int schemaVersion, List<Entry> entries) {
        public Snapshot { entries = List.copyOf(entries); }
    }
    private ClasspathInventory() { }

    public static Snapshot capture(String classpath) throws IOException {
        String[] paths = classpath.split(Pattern.quote(java.io.File.pathSeparator), -1);
        var entries = new ArrayList<Entry>();
        var seen = new java.util.HashSet<Path>();
        for (String value : paths) {
            if (value.isBlank()) throw new IllegalArgumentException("RUNTIME-INVENTORY: empty classpath entry");
            Path root = Path.of(value);
            if (!root.isAbsolute()) throw new IllegalArgumentException("RUNTIME-INVENTORY: absolute classpath required");
            root = root.normalize();
            physical(root);
            if (!seen.add(root)) throw new IllegalArgumentException("RUNTIME-INVENTORY: duplicate classpath entry");
            if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                var files = new ArrayList<FileStamp>();
                Path directory = root;
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        physical(dir);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        physical(file);
                        if (!attrs.isRegularFile()) throw new IOException("RUNTIME-INVENTORY: special file " + file);
                        files.add(new FileStamp(directory.relativize(file).toString().replace('\\', '/'), sha256(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });
                files.sort(Comparator.comparing(FileStamp::path));
                entries.add(new Entry(root.toString(), "directory", files));
            } else if (Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS)) {
                entries.add(new Entry(root.toString(), "file", List.of(new FileStamp("", sha256(root)))));
            } else {
                throw new IOException("RUNTIME-INVENTORY: missing classpath entry " + root);
            }
        }
        return new Snapshot(1, entries);
    }

    /**
     * Surefire reports one trailing separator in java.class.path. Remove that reporting padding
     * only when the actual loader exposes exactly the explicitly declared directory roots.
     * Standalone runtime inventories still reject every empty entry.
     */
    public static String executableTestClasspath(String reported, ClassLoader loader) throws IOException {
        String separator = java.io.File.pathSeparator;
        if (!reported.endsWith(separator)) return reported;
        String explicit = reported.substring(0, reported.length() - separator.length());
        String[] values = explicit.split(Pattern.quote(separator), -1);
        var expectedRoots = new java.util.HashSet<Path>();
        for (String value : values) {
            if (value.isBlank()) throw new IllegalArgumentException("RUNTIME-INVENTORY: ambiguous empty test classpath entry");
            Path path = Path.of(value);
            if (!path.isAbsolute()) throw new IllegalArgumentException("RUNTIME-INVENTORY: relative test classpath");
            if (Files.isDirectory(path)) expectedRoots.add(path.toRealPath());
        }
        var actualRoots = new java.util.HashSet<Path>();
        var resources = loader.getResources("");
        while (resources.hasMoreElements()) {
            var resource = resources.nextElement();
            if (resource.getProtocol().equals("file")) {
                try {
                    Path directory = Path.of(resource.toURI());
                    if (Files.isDirectory(directory)) actualRoots.add(directory.toRealPath());
                } catch (java.net.URISyntaxException invalid) { throw new IOException("Runtime root URI", invalid); }
            }
        }
        if (!actualRoots.equals(expectedRoots)) {
            throw new IllegalArgumentException("RUNTIME-INVENTORY: test loader roots differ; reported padding may expose a working directory");
        }
        return explicit;
    }

    private static void physical(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path) || !normalized.equals(path.toRealPath())) {
            throw new IOException("RUNTIME-INVENTORY: linked path " + path);
        }
    }

    public static String sha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(file)) {
                byte[] block = new byte[32768];
                int size;
                while ((size = stream.read(block)) != -1) digest.update(block, 0, size);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static void writeNew(Path file, Object value) throws IOException {
        byte[] json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        Files.write(file, json, java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}

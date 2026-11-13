package atf.rentz.distribution.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Shared receipt boundaries, hashes and sanitized diagnostics. */
public final class ReleaseEvidenceFiles {
    private ReleaseEvidenceFiles() { }

    public static String safeFailure(Exception failure, String fallback) {
        String message = failure.getMessage();
        if (message == null) return fallback;
        int separator = message.indexOf(':');
        String code = separator < 0 ? message : message.substring(0, separator);
        return code.matches("RAG-[A-Z0-9-]+") ? code : fallback;
    }

    public static Path externalRun(Path root, Path supplied) throws IOException {
        if (supplied == null) throw new IOException("RAG-RECEIPT-MISSING");
        assertNoSymlinkPath(supplied.toAbsolutePath().normalize());
        Path run = supplied.toRealPath();
        if (run.startsWith(root) || !Files.isDirectory(run, LinkOption.NOFOLLOW_LINKS)) throw new IOException("RAG-RECEIPT-BOUNDARY");
        return run;
    }

    public static byte[] readExternalFile(Path file, long maxBytes) throws IOException {
        assertNoSymlinkPath(file.toAbsolutePath().normalize());
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) == 0 || Files.size(file) > maxBytes) throw new IOException("RAG-RECEIPT-FILE");
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length == 0 || bytes.length > maxBytes) throw new IOException("RAG-RECEIPT-LIMIT");
        return bytes;
    }

    /** Reads numeric receipt fields without Jackson's truncating asInt conversions. */
    public static int exactCount(JsonNode value) throws IOException {
        if (value == null || !value.isNumber()) throw new IOException("RAG-RECEIPT-COUNT");
        try {
            int parsed = value.decimalValue().intValueExact();
            if (parsed < 0) throw new ArithmeticException("negative count");
            return parsed;
        } catch (ArithmeticException invalid) {
            throw new IOException("RAG-RECEIPT-COUNT", invalid);
        }
    }

    public static void assertNoSymlinkPath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path cursor = absolute.getRoot();
        for (Path part : absolute) {
            cursor = cursor.resolve(part);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor))
                throw new IOException("RAG-SYMLINK-PATH: " + cursor);
        }
    }

    public static String sha256(byte[] bytes) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IOException("SHA-256 unavailable", impossible); }
    }
}

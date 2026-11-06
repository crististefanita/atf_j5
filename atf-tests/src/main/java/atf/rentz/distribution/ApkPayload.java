package atf.rentz.distribution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

/** Complete bytes and ZIP consistency; Android signature and runtime acceptance remain separate. */
public final class ApkPayload {
    public static void verify(Path apk, long bytes, String expectedSha256) throws IOException {
        require(Files.isRegularFile(apk) && !Files.isSymbolicLink(apk), "APK-FILE", apk);
        require(bytes > 0 && Files.size(apk) == bytes, "APK-SIZE", apk);
        require(expectedSha256 != null && expectedSha256.matches("[0-9a-f]{64}") && hash(apk).equals(expectedSha256), "APK-HASH", apk);
        var names = new HashSet<String>();
        try (var zip = new ZipFile(apk.toFile())) {
            var entries = zip.entries();byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();String name = entry.getName();
                require(names.add(name), "APK-ENTRY-DUPLICATE", name);
                ReleasePaths.relative(entry.isDirectory() ? name.substring(0, name.length() - 1) : name);
                if (entry.isDirectory()) continue;
                CRC32 crc = new CRC32();long count = 0;
                try (var input = zip.getInputStream(entry)) { int read;
                    while ((read = input.read(buffer)) != -1) { crc.update(buffer, 0, read);count = Math.addExact(count, read); }
                }
                require(count == entry.getSize() && crc.getValue() == entry.getCrc(), "APK-CRC", name);
            }
        } catch (java.util.zip.ZipException corrupt) { throw new IOException("APK-ZIP: " + apk, corrupt); }
        require(names.contains("AndroidManifest.xml") && names.contains("classes.dex"), "APK-CONTENT", apk);
        require(Files.size(apk) == bytes && hash(apk).equals(expectedSha256), "APK-CHANGED", apk);
    }
    public static String hash(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void require(boolean valid, String code, Object context) throws IOException {
        if (!valid) throw new IOException(code + ": " + context);
    }
    private ApkPayload() { }
}

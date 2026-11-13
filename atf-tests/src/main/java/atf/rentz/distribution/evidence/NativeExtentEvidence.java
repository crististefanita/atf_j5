package atf.rentz.distribution.evidence;

import atf.reporting.evidence.ui.ExtentUiEvidenceMarkup;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.externalRun;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.readExternalFile;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256;

/** Verifies the published Extent checkpoints against the original native captures. */
public final class NativeExtentEvidence {
    private NativeExtentEvidence() { }

    private static final Pattern INLINE_PNG = Pattern.compile("data:image/png;base64,([^\\\"'\\s<>]+)");
    private static final Pattern NATIVE_EXTENT_STEP = Pattern.compile("<a class=\"node [^\"]*\" id=\"[0-9]+\"><span>THEN ([^<]+)</span></a>");
    private static final Pattern NATIVE_SCREENSHOT_ID = Pattern.compile(" id=\"(atf-screenshot-screenshot-[0-9]+)\"");
    private static final int MAX_INLINE_PNG_BYTES = 8 * 1024 * 1024;
    static void verify(String html, Path root, JsonNode receipt, JsonNode checkpoints) throws IOException {
        validateInlinePngs(html);
        Path raw = externalRun(root, Path.of(receipt.required("rawRoot").asText()));
        var steps = NATIVE_EXTENT_STEP.matcher(html);
        var files = new HashSet<String>();
        var capturesByDigest = new HashMap<String, String>();
        var captureIds = new HashSet<String>();
        boolean hasStep = steps.find();
        for (JsonNode checkpoint : checkpoints) {
            if (!hasStep || !steps.group(1).equals(ExtentUiEvidenceMarkup.escape(checkpoint.required("title").asText())))
                throw new IOException("RAG-UI-REPORT-CHECKPOINT");
            int start = steps.end();
            hasStep = steps.find();
            int end = hasStep ? steps.start() : html.length();
            byte[] image = nativeCapture(raw, checkpoint.required("image").asText(), files, MAX_INLINE_PNG_BYTES);
            nativeCapture(raw, checkpoint.required("hierarchy").asText(), files, 4L * 1024 * 1024);
            verifyNativeScreenshot(html, start, end, image, capturesByDigest, captureIds);
            int assertions = html.indexOf("<div class=\"atf-ui-assertions\"", start, end);
            if (assertions < 0) throw new IOException("RAG-UI-REPORT-COMPARISONS");
            int tableStart = html.indexOf("<tbody>", assertions, end);
            if (tableStart < 0) throw new IOException("RAG-UI-REPORT-COMPARISONS");
            int tableEnd = html.indexOf("</tbody>", tableStart, end);
            var rows = new StringBuilder("<tbody>");
            for (JsonNode check : checkpoint.required("checks")) {
                rows.append("<tr><td>").append(ExtentUiEvidenceMarkup.escape(check.required("name").asText()))
                        .append("</td><td>").append(ExtentUiEvidenceMarkup.escape(check.required("expected").asText()))
                        .append("</td><td>").append(ExtentUiEvidenceMarkup.escape(check.required("actual").asText()))
                        .append("</td><td>✅</td></tr>");
            }
            if (tableEnd != tableStart + rows.length() || !html.startsWith(rows.toString(), tableStart))
                throw new IOException("RAG-UI-REPORT-COMPARISONS");
        }
        if (hasStep) throw new IOException("RAG-UI-REPORT-CHECKPOINT");
    }

    private static void verifyNativeScreenshot(String html, int start, int end, byte[] image,
                                               Map<String, String> capturesByDigest, Set<String> captureIds) throws IOException {
        String digest = sha256(image);
        String previousCapture = capturesByDigest.get(digest);
        if (previousCapture != null && html.indexOf("href=\"#" + previousCapture + "\"", start, end) >= 0) return;
        String screenshot = "href=\"data:image/png;base64," + Base64.getEncoder().encodeToString(image) + "\"";
        int href = html.indexOf(screenshot, start, end);
        if (href < 0) throw new IOException("RAG-UI-REPORT-CAPTURE-MISMATCH");
        int linkEnd = html.indexOf(">", href + screenshot.length(), end);
        if (linkEnd < 0) throw new IOException("RAG-UI-REPORT-CAPTURE-MISMATCH");
        var id = NATIVE_SCREENSHOT_ID.matcher(html).region(href + screenshot.length(), linkEnd);
        if (!id.find() || !captureIds.add(id.group(1))) throw new IOException("RAG-UI-REPORT-CAPTURE-ID");
        capturesByDigest.put(digest, id.group(1));
    }

    private static byte[] nativeCapture(Path raw, String name, Set<String> files, long limit) throws IOException {
        if (!name.matches("[A-Za-z0-9_-]+\\.(?:png|txt)") || !files.add(name.toLowerCase(Locale.ROOT)))
            throw new IOException("RAG-UI-REPORT-CAPTURE-PATH");
        return readExternalFile(raw.resolve(name), limit);
    }

    private static int countOccurrences(String value, String token) {
        int count = 0, offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) { count++; offset += token.length(); }
        return count;
    }

    public static int validateInlinePngs(String html) throws IOException {
        String marker = "data:image/png;base64,";
        int declared = countOccurrences(html, marker), valid = 0;
        var matcher = INLINE_PNG.matcher(html);
        while (matcher.find()) {
            String encoded = matcher.group(1);
            if (encoded.length() > ((MAX_INLINE_PNG_BYTES + 2L) / 3L) * 4L)
                throw new IOException("RAG-UI-REPORT-PNG-LIMIT");
            byte[] png;
            try { png = Base64.getDecoder().decode(encoded); }
            catch (IllegalArgumentException invalid) { throw new IOException("RAG-UI-REPORT-PNG-BASE64", invalid); }
            validatePngChunks(png);
            valid++;
        }
        if (declared != valid) throw new IOException("RAG-UI-REPORT-PNG-URI");
        return valid;
    }

    private static void validatePngChunks(byte[] png) throws IOException {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        if (png.length < 45 || png.length > MAX_INLINE_PNG_BYTES) throw new IOException("RAG-UI-REPORT-PNG-LIMIT");
        for (int i = 0; i < signature.length; i++) if (png[i] != signature[i]) throw new IOException("RAG-UI-REPORT-PNG-SIGNATURE");
        int offset = signature.length, chunks = 0, width = 0, height = 0;
        boolean ihdr = false, idat = false, ended = false;
        while (offset < png.length) {
            if (png.length - offset < 12 || ++chunks > 10_000) throw new IOException("RAG-UI-REPORT-PNG-CHUNK");
            long length = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(png, offset, 4).getInt());
            long end = (long) offset + 12L + length;
            if (length > MAX_INLINE_PNG_BYTES || end > png.length) throw new IOException("RAG-UI-REPORT-PNG-CHUNK");
            String type = new String(png, offset + 4, 4, StandardCharsets.US_ASCII);
            if (!type.matches("[A-Za-z]{4}")) throw new IOException("RAG-UI-REPORT-PNG-CHUNK-TYPE");
            if (!ihdr) {
                if (!type.equals("IHDR") || length != 13) throw new IOException("RAG-UI-REPORT-PNG-IHDR");
                width = java.nio.ByteBuffer.wrap(png, offset + 8, 4).getInt();
                height = java.nio.ByteBuffer.wrap(png, offset + 12, 4).getInt();
                if (width < 1 || height < 1 || (long) width * height > 24_000_000L)
                    throw new IOException("RAG-UI-REPORT-PNG-DIMENSIONS");
                ihdr = true;
            } else if (type.equals("IHDR")) throw new IOException("RAG-UI-REPORT-PNG-IHDR");
            if (type.equals("IDAT")) idat = true;
            CRC32 crc = new CRC32(); crc.update(png, offset + 4, Math.toIntExact(length) + 4);
            long expectedCrc = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(png, offset + 8 + Math.toIntExact(length), 4).getInt());
            if (crc.getValue() != expectedCrc) throw new IOException("RAG-UI-REPORT-PNG-CRC");
            offset = Math.toIntExact(end);
            if (type.equals("IEND")) {
                if (length != 0 || !idat || offset != png.length) throw new IOException("RAG-UI-REPORT-PNG-END");
                ended = true; break;
            }
        }
        if (!ihdr || !idat || !ended) throw new IOException("RAG-UI-REPORT-PNG-INCOMPLETE");
    }

}

package atf.rentz.distribution;

import atf.rentz.distribution.evidence.NativeExtentEvidence;

import atf.testkit.AtfComponentTest;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class ReleaseReadinessRagPngTest {
    @Test void acceptsCompleteInlinePngChunksAndCountsEachImageOnce() throws Exception {
        String uri = pngDataUri();
        assertEquals(2, NativeExtentEvidence.validateInlinePngs("<img src=\"" + uri + "\"/><img src='" + uri + "'/>") );
        assertEquals(16, NativeExtentEvidence.validateInlinePngs("<body>" + ("<img src=\"" + uri + "\"/>").repeat(16) + "</body>"));
    }

    @Test void rejectsMalformedUriCorruptCrcAndExcessiveDimensions() throws Exception {
        assertThrows(IOException.class, () -> NativeExtentEvidence.validateInlinePngs("<img src=\"data:image/png;base64,AAAA\"/>"));
        byte[] corrupt = Base64.getDecoder().decode(pngDataUri().substring("data:image/png;base64,".length()));
        corrupt[corrupt.length - 5] ^= 0x01;
        String damaged = "data:image/png;base64," + Base64.getEncoder().encodeToString(corrupt);
        assertThrows(IOException.class, () -> NativeExtentEvidence.validateInlinePngs("<img src=\"" + damaged + "\"/>"));
        assertThrows(IOException.class, () -> NativeExtentEvidence.validateInlinePngs("<img src=\"" + pngDataUri(16_000, 16_000) + "\"/>"));
    }

    private static String pngDataUri() throws IOException { return pngDataUri(1, 1); }

    private static String pngDataUri(int width, int height) throws IOException {
        var png = new ByteArrayOutputStream();
        png.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        chunk(png, "IHDR", java.nio.ByteBuffer.allocate(13).putInt(width).putInt(height).put(new byte[]{8, 6, 0, 0, 0}).array());
        var pixels = new ByteArrayOutputStream();
        try (var deflater = new DeflaterOutputStream(pixels)) { deflater.write(new byte[]{0, 0, 0, 0, 0}); }
        chunk(png, "IDAT", pixels.toByteArray());
        chunk(png, "IEND", new byte[0]);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
    }

    private static void chunk(ByteArrayOutputStream png, String name, byte[] data) throws IOException {
        byte[] type = name.getBytes(StandardCharsets.US_ASCII);
        var checksumInput = new ByteArrayOutputStream(); checksumInput.write(type); checksumInput.write(data);
        var crc = new CRC32(); crc.update(checksumInput.toByteArray());
        var output = new DataOutputStream(png);
        output.writeInt(data.length); output.write(type); output.write(data); output.writeInt((int) crc.getValue());
    }
}

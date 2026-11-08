package ro.rentzlab.app.ui.libraries;

import org.junit.*;
import ro.rentzlab.app.testing.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.zip.*;
import java.io.*;
import java.security.MessageDigest;

@AndroidComponentLayer
public final class CardUiAdoptionTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    @Test public void pinnedArchiveAndLoadedPresentationTypesHaveTheQualifiedBytesWithoutGameOrAtfClasses() throws Exception {
        Path root = Path.of(System.getProperty("rentz.uiRepository"));
        var provenance = JsonParser.parseString(new String(Files.readAllBytes(root.resolve("provenance.json")), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        audit.equal("Adopted UI coordinate", "ro.cardtable:card-table-ui:0.1.7", provenance.get("coordinate").getAsString());
        audit.equal("Adopted Java coordinate", "ro.cardtable:card-table-names:0.1.0", provenance.get("namesCoordinate").getAsString());
        for (var file : provenance.getAsJsonArray("files")) {
            var value = file.getAsJsonObject(); audit.equal("Pinned " + value.get("path").getAsString(), value.get("sha256").getAsString(), hash(Files.readAllBytes(root.resolve(value.get("path").getAsString()))));
        }
        Path aar = root.resolve("ro/cardtable/card-table-ui/0.1.7/card-table-ui-0.1.7.aar");
        byte[] classes;
        try (var zip = new ZipFile(aar.toFile())) { classes = zip.getInputStream(zip.getEntry("classes.jar")).readAllBytes(); }
        verifyClassBytes(classes, ro.cardtable.ui.seat.SeatContent.class, ro.cardtable.ui.animation.TrickCollectionRun.class,
                ro.cardtable.ui.table.BoardLayout.class, ro.cardtable.ui.table.TableGeometry.class,
                ro.cardtable.ui.table.TableSeatLayout.class, ro.cardtable.ui.table.TableSizing.class,
                ro.cardtable.ui.table.PlayedCardMetrics.class, ro.cardtable.ui.table.PlayedCardsView.class,
                ro.cardtable.ui.table.TableViewportPolicy.class, ro.cardtable.ui.turn.TurnPacing.class);
        verifyClassBytes(Files.readAllBytes(root.resolve("ro/cardtable/card-table-names/0.1.0/card-table-names-0.1.0.jar")),
                ro.cardtable.names.PlayerNames.class);
        audit.rejects("No Whist engine on Android JVM classpath", ClassNotFoundException.class, () -> Class.forName("ro.whistlab.core.session.GameSession"));
    }
    private void verifyClassBytes(byte[] classes, Class<?>... types) throws Exception {
        var markers = new java.util.LinkedHashMap<String, Class<?>>();
        for (Class<?> type : types) markers.put(type.getName().replace('.', '/') + ".class", type);
        var qualified = new java.util.HashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(classes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                audit.truth("UI owns its class namespace " + entry.getName(), !entry.getName().startsWith("ro/whistlab/") && !entry.getName().startsWith("ro/rentzlab/") && !entry.getName().startsWith("atf/"));
                if (markers.containsKey(entry.getName())) qualified.put(entry.getName(), zip.readAllBytes());
            }
        }
        for (var entry : markers.entrySet()) {
            String marker = entry.getKey();
            audit.truth("Qualified marker present " + marker, qualified.containsKey(marker));
            try (var stream = entry.getValue().getClassLoader().getResourceAsStream(marker)) {
                audit.equal("Loaded marker equals qualified binary " + marker, hash(qualified.get(marker)), hash(stream.readAllBytes()));
            }
        }
    }
    private String hash(byte[] bytes) throws Exception {
        StringBuilder value = new StringBuilder();
        for (byte part : MessageDigest.getInstance("SHA-256").digest(bytes)) value.append(String.format("%02x", part & 255));
        return value.toString();
    }
}

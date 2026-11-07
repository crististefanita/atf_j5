package ro.rentzlab.app.lifecycle;

import ro.rentzlab.app.testing.AndroidComponentLayer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import static org.junit.Assert.*;

@AndroidComponentLayer
public class FileSessionStorageTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void everyWriteUsesADifferentStagingFileOutsideCanonicalData() throws Exception {
        var data=folder.newFolder("data").toPath();var staging=folder.newFolder("staging").toPath();var paths=new ArrayList<Path>();
        var store=new FileSessionStorage(data,staging,(from,to)->{paths.add(from);Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);});
        store.write("session.json","first");store.write("session.json","second");
        assertEquals("second",store.read());assertNotEquals(paths.get(0),paths.get(1));assertEquals(staging,paths.get(0).getParent());
        try(var files=Files.list(data)){assertEquals(1,files.count());}
    }
    @Test public void failedAtomicReplacePreservesOriginalAndCleansOnlyItsOwnStagingFile() throws Exception {
        var data=folder.newFolder("data").toPath();var staging=folder.newFolder("staging").toPath();Files.write(data.resolve("session.json"),"original".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var unrelated=staging.resolve("unrelated.keep");Files.write(unrelated,"keep".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var store=new FileSessionStorage(data,staging,(from,to)->{throw new IOException("injected atomic move failure");});
        try{store.write("session.json","candidate");fail("Move failure hidden");}catch(IOException expected){}
        assertEquals("original",store.read());assertEquals("keep",new String(Files.readAllBytes(unrelated),java.nio.charset.StandardCharsets.UTF_8));
        try(var files=Files.list(staging)){assertEquals(1,files.count());}
    }
    @Test public void missingDataAndUnexpectedFileNamesNeverCreateAReplacement() throws Exception {
        var data=folder.newFolder("data").toPath();var staging=folder.newFolder("staging").toPath();var store=new FileSessionStorage(data,staging);
        assertNull(store.read());try{store.write("../outside","bad");fail("Unexpected name accepted");}catch(IllegalArgumentException expected){}
        try(var files=Files.list(data)){assertEquals(0,files.count());}
    }
    @Test public void defaultAtomicBackendReplacesBothCanonicalFilesAndLeavesNoStagingResidue()throws Exception{
        var data=folder.newFolder("atomic-data").toPath();var staging=folder.newFolder("atomic-staging").toPath();var store=new FileSessionStorage(data,staging);
        store.write("session.json","first");store.write("session.json","second");store.write("actor-view.json","public");
        assertEquals("second",store.read());assertEquals("public",new String(Files.readAllBytes(data.resolve("actor-view.json")),java.nio.charset.StandardCharsets.UTF_8));
        try(var files=Files.list(staging)){assertEquals(0,files.count());}
    }
}

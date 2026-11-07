package atf.rentz.governance.runtime;

import atf.rentz.runtime.ClasspathInventory;
import atf.testkit.AtfComponentTest;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class ClasspathInventoryTest {
    @TempDir Path root;

    @Test void snapshotCoversResourcesHelpersAndDependencyBytesWithoutDependingOnTimestamps() throws Exception {
        Path classes = Files.createDirectories(root.resolve("classes/model"));
        Path helper = Files.writeString(classes.resolve("Helper.class"), "helper-original");
        Files.writeString(classes.resolve("rules.json"), "rules-original");
        Path dependency = Files.writeString(root.resolve("dependency.jar"), "jar-original");
        String cp = classes.getParent() + File.pathSeparator + dependency;
        var before = ClasspathInventory.capture(cp);
        assertEquals(2, before.entries().size());
        assertEquals("model/Helper.class", before.entries().get(0).files().get(0).path());
        assertEquals(2, before.entries().get(0).files().size());
        assertEquals(before, ClasspathInventory.capture(cp));
        Files.setLastModifiedTime(helper, java.nio.file.attribute.FileTime.fromMillis(0));
        assertEquals(before, ClasspathInventory.capture(cp), "Timestamp is not byte identity");
        Files.writeString(helper, "helper-changed");
        assertNotEquals(before, ClasspathInventory.capture(cp));
        Files.writeString(helper, "helper-original");
        assertEquals(before, ClasspathInventory.capture(cp));
        Files.writeString(dependency, "jar-changed");
        assertNotEquals(before, ClasspathInventory.capture(cp));
    }

    @Test void testReporterPaddingNeedsAnIndependentLoaderRootProof() throws Exception {
        Path declared = Files.createDirectory(root.resolve("declared"));
        Path extra = Files.createDirectory(root.resolve("implicit-working-directory"));
        String cp = declared + File.pathSeparator;
        try (var good = new java.net.URLClassLoader(new java.net.URL[]{declared.toUri().toURL()}, null);
             var bad = new java.net.URLClassLoader(new java.net.URL[]{declared.toUri().toURL(), extra.toUri().toURL()}, null)) {
            assertEquals(declared.toString(), ClasspathInventory.executableTestClasspath(cp, good));
            assertThrows(IllegalArgumentException.class, () -> ClasspathInventory.executableTestClasspath(cp, bad));
            assertThrows(IllegalArgumentException.class, () -> ClasspathInventory.capture(cp));
        }
    }

    @Test void changesInPopulationAndClasspathOrderCannotReuseTheOriginalSnapshot() throws Exception {
        Path classes = Files.createDirectory(root.resolve("classes"));
        Path main = Files.writeString(classes.resolve("Entry.class"), "main");
        Path jar = Files.writeString(root.resolve("dep.jar"), "dependency");
        String cp = classes + File.pathSeparator + jar;
        var before = ClasspathInventory.capture(cp);
        assertNotEquals(before, ClasspathInventory.capture(jar + File.pathSeparator + classes));
        Path extra = Files.writeString(classes.resolve("Extra.class"), "extra");
        assertNotEquals(before, ClasspathInventory.capture(cp));
        Files.delete(extra);
        assertEquals(before, ClasspathInventory.capture(cp));
        Files.delete(main);
        assertNotEquals(before, ClasspathInventory.capture(cp));
        assertThrows(IllegalArgumentException.class, () -> ClasspathInventory.capture(cp + File.pathSeparator));
        assertThrows(IllegalArgumentException.class, () -> ClasspathInventory.capture("relative-directory"));
        assertThrows(IllegalArgumentException.class, () -> ClasspathInventory.capture(cp + File.pathSeparator + jar));
    }
}

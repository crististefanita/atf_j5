package atf.rentz.distribution;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static atf.testkit.assertions.AuditedAssertions.*;

@AtfComponentTest
class AtfReportingLibraryTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings={"1.0.0-j23", "1.0.0-j24"})
    void rootMavenVersionSelectsAndHashesItsMatchingJar(String version) throws Exception {
        Path root=fixture(version);
        var identity=AtfReportingLibrary.resolve(root);
        Path expected=root.resolve("lib/atf-reporting-"+version+".jar");
        assertEquals(version,identity.version());
        assertEquals("atf-reporting-"+version,identity.artifactName());
        assertEquals(expected,identity.jar());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(expected))),identity.sha256());
    }

    @Test
    void missingOrAmbiguousVersionAndExternalEntitiesFailClosed() throws Exception {
        Path root=fixture("1.0.0-j24");
        Files.writeString(root.resolve("pom.xml"),"<project><properties><atf.reporting.version>1.0.0-j24</atf.reporting.version><atf.reporting.version>1.0.0-j25</atf.reporting.version></properties></project>");
        assertEquals("RAG-REPORTING-VERSION-COUNT",assertThrows(IOException.class,()->AtfReportingLibrary.resolve(root)).getMessage());
        Files.writeString(root.resolve("pom.xml"),"<!DOCTYPE project [<!ENTITY x SYSTEM 'file:///C:/sensitive'>]><project><properties><atf.reporting.version>&x;</atf.reporting.version></properties></project>");
        assertThrows(IOException.class,()->AtfReportingLibrary.resolve(root));
    }

    private Path fixture(String version) throws Exception {
        Path root=Files.createDirectories(temporary.resolve(version));
        Files.createDirectories(root.resolve("lib"));
        Files.writeString(root.resolve("pom.xml"),"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><properties><atf.reporting.version>"+version+"</atf.reporting.version></properties></project>");
        Files.write(root.resolve("lib/atf-reporting-"+version+".jar"),new byte[]{1,2,3});
        return root;
    }
}

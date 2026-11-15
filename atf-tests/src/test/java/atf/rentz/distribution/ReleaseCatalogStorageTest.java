package atf.rentz.distribution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseCatalogStorageTest {
    @TempDir Path temporary;

    @Test void managedReleaseRejectsLocalApkAndChecksumEvenBeforePackaging() throws IOException {
        Path project = temporary.resolve("project");
        Path edition = project.resolve("release/1.2.0");
        Path data = temporary.resolve("data");
        Files.createDirectories(edition.resolve("reports"));
        Files.createDirectories(project.resolve("rentz-lab-app/app"));
        Files.createDirectory(data);
        Files.writeString(project.resolve("README.md"), """
                # Rentz
                ## Jocul
                - [Documentație](release/1.2.0/README.md)
                ## Documentație
                - [Catalog](release/README.md)
                """);
        Files.writeString(project.resolve("rentz-lab-app/app/build.gradle"),
                "versionName '1.2.0'; versionCode 3; minSdk 26; targetSdk 36");
        Files.writeString(project.resolve("release/index.json"), """
                {"schemaVersion":1,"repository":"example/game","currentVersion":"1.2.0","versions":["1.2.0"]}
                """);
        Files.writeString(project.resolve("release/README.md"), "[Ediție](1.2.0/README.md)");
        for (String file : List.of("README.md", "reports/README.md", "release-notes.md")) {
            Files.writeString(edition.resolve(file), "# Ediție");
        }
        Files.writeString(edition.resolve("release-validation.json"), """
                {"release":{"versionName":"1.2.0","versionCode":3,"applicationId":"ro.rentzlab.app","minSdk":26,
                  "targetSdk":36,"apkName":"RentzLab-1.2.0.apk","apkBytes":null,"sha256":null},
                  "publication":{"state":"DRAFT"}}
                """);

        assertEquals("DRAFT", ReleaseCatalog.verify(project, false, data.toString()).state());
        for (Path local : List.of(project.resolve("release/RentzLab-1.1.0.apk"),
                edition.resolve("RentzLab-1.2.0.apk"), edition.resolve("RentzLab-1.2.0.apk.sha256"))) {
            Files.writeString(local, "local leftover");
            try {
                IOException refusal = assertThrows(IOException.class,
                        () -> ReleaseCatalog.verify(project, false, data.toString()), local.toString());
                assertEquals("RELEASE-BINARY-IN-DOCUMENTATION", refusal.getMessage());
            } finally {
                Files.delete(local);
            }
        }
    }
}

package atf.testkit.output;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfUnitTest
class ReportOutputDirectoryTest {
    @TempDir Path project;

    @UiReportSubdirectory("owner/ui")
    private static class UiOwner { }

    @ProjectRelativeUiReportDirectory("owner/project")
    private static class ProjectOwner { }

    @UiReportSubdirectory("base")
    private static class ParentOwner { }

    @ProjectRelativeUiReportDirectory("child")
    private static final class ChildOwner extends ParentOwner { }

    @UiReportSubdirectory("one")
    @ProjectRelativeUiReportDirectory("two")
    private static class ConflictOwner { }

    @UiReportSubdirectory("../escape")
    private static class EscapeOwner { }

    @ProjectRelativeUiReportDirectory("C:drive-relative")
    private static class DriveRelativeOwner { }

    @ProjectRelativeUiReportDirectory("https://example.invalid/reports")
    private static class UriOwner { }

    @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
    @UiReportSubdirectory("owner/ui")
    private @interface ComposedUiOutput { }

    @ComposedUiOutput private static class ComposedOwner { }
    @ComposedUiOutput @UiReportSubdirectory("owner/ui")
    private static class EquivalentOwner { }
    @ComposedUiOutput @ProjectRelativeUiReportDirectory("other")
    private static class ComposedConflictOwner { }

    @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
    @CycleB private @interface CycleA { }
    @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
    @CycleA @ProjectRelativeUiReportDirectory("cyclic")
    private @interface CycleB { }
    @CycleA private static class CyclicMetaOwner { }

    @ProjectRelativeUiReportDirectory("  ") private static class BlankOwner { }
    @ProjectRelativeUiReportDirectory("//server/share") private static class UncOwner { }
    @ProjectRelativeUiReportDirectory("../outside") private static class ProjectEscapeOwner { }
    @ProjectRelativeUiReportDirectory("owner/./project") private static class NormalizedOwner { }
    @UiReportSubdirectory("résultats de test/é2e") private static class UnicodeAndSpaceOwner { }
    @UiReportSubdirectory("bad<path") private static class InvalidNativePathOwner { }
    @UiReportSubdirectory("junction/new/nested") private static class JunctionChildOwner { }

    @Test
    void anchorsStayDistinctAndInheritanceCanBeOverridden() {
        Path ui = project.resolve("artifacts/reports/ui");
        assertEquals(ui.resolve("owner/ui"), ReportOutputDirectory.resolve(project, ui, UiOwner.class));
        assertEquals(project.resolve("owner/project"), ReportOutputDirectory.resolve(project, ui, ProjectOwner.class));
        assertEquals(project.resolve("child"), ReportOutputDirectory.resolve(project, ui, ChildOwner.class));
        assertEquals(ui.resolve("owner/ui"), ReportOutputDirectory.resolve(project, ui, ComposedOwner.class));
        assertEquals(ui.resolve("owner/ui"), ReportOutputDirectory.resolve(project, ui, EquivalentOwner.class));
        assertEquals(project.resolve("cyclic"), ReportOutputDirectory.resolve(project, ui, CyclicMetaOwner.class));
        assertEquals(project.resolve("owner/project"), ReportOutputDirectory.resolve(project, ui, NormalizedOwner.class));
        assertEquals(ui.resolve("résultats de test/é2e"),
                ReportOutputDirectory.resolve(project, ui, UnicodeAndSpaceOwner.class));
        assertEquals(ui, ReportOutputDirectory.resolve(project, ui, null));
    }

    @Test
    void rejectsConflictAndUnsafePathsBeforePublication() {
        Path ui = project.resolve("artifacts/reports/ui");
        assertThrows(IllegalStateException.class, () -> ReportOutputDirectory.resolve(project, ui, ConflictOwner.class));
        assertThrows(IllegalStateException.class, () -> ReportOutputDirectory.resolve(project, ui, ComposedConflictOwner.class));
        assertThrows(IllegalStateException.class, () -> ReportOutputDirectory.resolve(project, ui, EscapeOwner.class));
        assertThrows(IllegalStateException.class, () -> ReportOutputDirectory.resolve(project, ui, DriveRelativeOwner.class));
        assertThrows(IllegalStateException.class, () -> ReportOutputDirectory.resolve(project, ui, UriOwner.class));
        assertThrows(IllegalStateException.class, () -> ReportOutputDirectory.resolve(project, ui, BlankOwner.class));
        assertThrows(IllegalStateException.class, () -> ReportOutputDirectory.resolve(project, ui, UncOwner.class));
        assertThrows(IllegalStateException.class, () -> ReportOutputDirectory.resolve(project, ui, ProjectEscapeOwner.class));
    }

    @Test
    void projectRelativeArtifactDirectoryUsesDefaultAndExplicitValuesUnderTheProjectRoot() {
        assertEquals(project.resolve("artifacts/reports/api/default"),
                ReportOutputDirectory.resolveProjectRelative(project, null,
                        "artifacts/reports/api/default"));
        assertEquals(project.resolve("custom/api-reports"),
                ReportOutputDirectory.resolveProjectRelative(project, "custom/api-reports",
                        "artifacts/reports/api/default"));
    }

    @Test
    void projectRelativeArtifactDirectoryRejectsTraversalAbsoluteUriAndDriveRelativePaths() {
        assertThrows(IllegalArgumentException.class,
                () -> ReportOutputDirectory.resolveProjectRelative(project, "../outside", "reports/api"));
        assertThrows(IllegalArgumentException.class,
                () -> ReportOutputDirectory.resolveProjectRelative(project,
                        project.resolve("outside").toString(), "reports/api"));
        assertThrows(IllegalArgumentException.class,
                () -> ReportOutputDirectory.resolveProjectRelative(project,
                        "https://example.invalid/reports", "reports/api"));
        assertThrows(IllegalArgumentException.class,
                () -> ReportOutputDirectory.resolveProjectRelative(project,
                        "C:drive-relative", "reports/api"));
    }

    @Test
    void projectRelativeArtifactDirectoryRejectsExistingReparsePointThatLeavesTheProject() throws Exception {
        Path root = Files.createDirectories(project.resolve("workspace"));
        Path outside = Files.createDirectories(project.resolve("outside"));
        Path link = root.resolve("linked");
        assumeTrue(ReportOutputSymlinkFixture.createDirectoryEscapePointIfSupported(link, outside),
                "OS does not allow creating a directory reparse-point fixture");

        try {
            assertThrows(IllegalStateException.class,
                    () -> ReportOutputDirectory.resolveProjectRelative(root,
                            "linked/new/nested", "reports/api"));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    void invalidNativePathHasAnActionableOwnerAndAnchorDiagnostic() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("windows"),
                "the invalid character in this fixture is Windows-specific");
        Path ui = project.resolve("artifacts/reports/ui");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ReportOutputDirectory.resolve(project, ui, InvalidNativePathOwner.class));

        assertEquals(java.nio.file.InvalidPathException.class, failure.getCause().getClass());
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains(
                InvalidNativePathOwner.class.getName()));
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains(ui.toString()));
    }

    @Test
    void directoryReparsePointCannotEscapeExistingAnchor() throws Exception {
        Path ui = Files.createDirectories(project.resolve("artifacts/reports/ui"));
        Path outside = Files.createDirectories(project.resolve("outside"));
        assumeTrue(ReportOutputSymlinkFixture.createDirectoryEscapePointIfSupported(
                        ui.resolve("owner"), outside),
                "OS does not allow creating a directory reparse-point fixture");
        assertThrows(IllegalStateException.class,
                () -> ReportOutputDirectory.resolve(project, ui, UiOwner.class));
    }

    @Test
    void junctionCannotEscapeAnchorWhenDestinationDoesNotExistYet() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("windows"),
                "directory junctions are a Windows-specific boundary");
        Path ui = Files.createDirectories(project.resolve("artifacts/reports/ui"));
        Path outside = Files.createDirectories(project.resolve("outside"));
        assumeTrue(ReportOutputSymlinkFixture.createJunctionIfSupported(ui.resolve("junction"), outside),
                "runner cannot create a directory junction");

        assertThrows(IllegalStateException.class,
                () -> ReportOutputDirectory.resolve(project, ui, JunctionChildOwner.class));
    }
}

package atf.testkit.output;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Optional OS facility used only by the symlink boundary probe. */
final class ReportOutputSymlinkFixture {
    private ReportOutputSymlinkFixture() { }

    static boolean createIfSupported(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (FileSystemException | UnsupportedOperationException unavailable) {
            return false;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot create symlink fixture: " + link, error);
        }
    }

    /**
     * Creates the strongest directory reparse-point available to the runner.
     * Windows runners without symbolic-link privilege still validate the same
     * publication boundary through a directory junction.
     */
    static boolean createDirectoryEscapePointIfSupported(Path link, Path target) {
        if (createIfSupported(link, target)) return true;
        return createJunctionIfSupported(link, target);
    }

    static boolean createJunctionIfSupported(Path link, Path target) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) return false;
        try {
            Files.createDirectories(link.getParent());
            String command = "mklink /J \"" + link.toAbsolutePath() + "\" \""
                    + target.toAbsolutePath() + "\"";
            Process process = new ProcessBuilder("cmd.exe", "/d", "/c", command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0 && Files.isDirectory(link);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.io.IOException | UnsupportedOperationException unavailable) {
            return false;
        }
    }
}

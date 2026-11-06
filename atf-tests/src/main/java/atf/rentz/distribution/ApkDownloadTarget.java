package atf.rentz.distribution;

/** Immutable expected identity declared before accessing GitHub. */
public record ApkDownloadTarget(String repository, String version, long bytes, String sha256) {
    public ApkDownloadTarget {
        if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                || version == null || !version.matches("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?")
                || bytes <= 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid declared APK download identity");
    }
    public String fileName() { return "RentzLab-" + version + ".apk"; }
    public String publicUrl() { return "https://github.com/" + repository + "/releases/download/v" + version + "/" + fileName(); }
}

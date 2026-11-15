package atf.rentz.distribution.publication;

import atf.rentz.distribution.publication.ReleasePublication.PromotionLease;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** A single remote ref coordinates promotion across independent checkouts and journals. */
final class GitPromotionChannel {
    static final String REF = "refs/tags/atf-rentz-promotion-lock";
    private final Path checkout;
    private final String remote;

    GitPromotionChannel(Path checkout, String remote) {
        this.checkout = checkout;
        this.remote = remote;
    }

    PromotionLease reserve(String tag) throws IOException {
        String tree = objectId(git("mktree"));
        String owner = objectId(git("-c", "user.name=Rentz publication", "-c", "user.email=rentz-publication@localhost",
                "commit-tree", "--no-gpg-sign", tree, "-m", "Rentz promotion " + tag + " " + UUID.randomUUID()));
        if (update("", owner + ":" + REF) != 0) {
            String observed = owner();
            if (observed != null && !observed.equals(owner)) throw new IOException("PUBLICATION-CHANNEL-IN-USE");
            throw new IOException("PUBLICATION-CHANNEL-ACQUIRE");
        }
        return new PromotionLease(owner, () -> release(owner));
    }

    String owner() throws IOException {
        Result result = git("ls-remote", "--refs", remote, REF);
        if (result.exit() != 0) throw new IOException("PUBLICATION-CHANNEL-READ");
        if (result.output().isEmpty()) return null;
        String[] ref = result.output().split("\t", -1);
        if (ref.length != 2 || !REF.equals(ref[1])) throw new IOException("PUBLICATION-CHANNEL-READ");
        return objectId(new Result(0, ref[0]));
    }

    void release(String expectedOwner) throws IOException {
        if (!isObjectId(expectedOwner) || update(expectedOwner, ":" + REF) != 0) {
            throw new IOException("PUBLICATION-CHANNEL-RELEASE");
        }
    }

    private int update(String expected, String refspec) throws IOException {
        return git("push", "--porcelain", "--no-progress", "--no-follow-tags",
                "--force-with-lease=" + REF + ":" + expected, remote, refspec).exit();
    }

    private static String objectId(Result result) throws IOException {
        if (result.exit() != 0 || !isObjectId(result.output())) throw new IOException("PUBLICATION-CHANNEL-GIT");
        return result.output();
    }

    private static boolean isObjectId(String hash) { return hash != null && hash.matches("[a-f0-9]{40}"); }

    private Result git(String... arguments) throws IOException {
        var command = new ArrayList<>(List.of("git", "-C", checkout.toString()));
        command.addAll(List.of(arguments));
        var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GCM_INTERACTIVE", "Never");
        Process process = builder.start();
        try {
            process.getOutputStream().close();
            if (!process.waitFor(90, TimeUnit.SECONDS)) throw new IOException("PUBLICATION-CHANNEL-GIT-TIMEOUT");
            try (var output = process.getInputStream()) {
                byte[] bytes = output.readNBytes(4097);
                if (bytes.length > 4096) throw new IOException("PUBLICATION-CHANNEL-GIT");
                return new Result(process.exitValue(), new String(bytes, StandardCharsets.UTF_8).strip());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("PUBLICATION-CHANNEL-GIT-INTERRUPTED");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private record Result(int exit, String output) { }
}

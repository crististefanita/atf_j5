package atf.rentz.governance.release;

import atf.rentz.RentzRulePublication;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.AuditedAssertions;
import atf.testkit.assertions.ComponentAuditExtension;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Effective attributes, including negative overrides, work without a .git directory in the source snapshot. */
@AtfComponentTest
class ReleaseGitPolicyTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT=RentzRulePublication.scoped(ReleaseGitPolicyTest.class,
            "release-git-policy.html","Git byte preservation; own temporary metadata, no source mutation");
    @TempDir Path temporary;
    private Path attributeRepository;

    @Test void releaseAndApkBuildInputsDisableGitTextConversion() throws Exception {
        Path root=atf.rentz.distribution.ReleaseCatalog.root();
        String version=atf.rentz.distribution.ReleaseCatalog.read(root.resolve("release/index.json")).required("currentVersion").asText();
        var paths=new ArrayList<>(List.of("release/"+version+"/README.md","release/"+version+"/RentzLab-"+version+".apk",
                "release/"+version+"/reports/component.html","rentz-lab-app/app/build.gradle"));
        try (var files=Files.list(root.resolve("lib"))) {
            files.filter(p -> p.toString().endsWith(".jar") || p.toString().endsWith(".pom"))
                    .forEach(p -> paths.add("lib/"+p.getFileName()));
        }
        AuditedAssertions.assertEquals(List.of(),textConversions(root,paths),"Git must preserve hashed build inputs and release evidence");
    }

    @Test void missingAutomaticForcedAndNestedTextRulesAreRejected() throws Exception {
        Path fixture = Files.createDirectory(temporary.resolve("repository with spaces"));
        AuditedAssertions.assertFalse(Files.exists(fixture.resolve(".git")), "TMP source copies do not require repository metadata");
        String evidence = "release/1.2.0/reports/report.html";
        for (String policy : List.of("", "release/** text=auto\n", "release/** text eol=crlf\n",
                "release/** -text\nrelease/1.2.0/reports/*.html text\n")) {
            Files.writeString(fixture.resolve(".gitattributes"), policy);
            AuditedAssertions.assertFalse(textConversions(fixture, List.of(evidence)).isEmpty(),
                    "Missing or overriding attributes must not qualify immutable evidence");
        }
        Files.writeString(fixture.resolve(".gitattributes"), "release/** -text\n");
        AuditedAssertions.assertEquals(List.of(), textConversions(fixture, List.of(evidence)),
                "The healthy neighboring policy preserves evidence bytes");
    }

    private List<String> textConversions(Path root, List<String> paths) throws Exception {
        if (attributeRepository == null) {
            attributeRepository = Files.createDirectory(temporary.resolve("attribute inspection"));
            git(attributeRepository, List.of("init", "--quiet"));
        }
        var arguments = new ArrayList<>(List.of("--git-dir=" + attributeRepository.resolve(".git"),
                "--work-tree=" + root, "-c", "core.autocrlf=true", "check-attr", "-z", "text", "--"));
        arguments.addAll(paths);
        String[] records = git(root, arguments).split("\\x00");
        if (records.length != paths.size() * 3) throw new IllegalStateException("RELEASE-GIT-ATTR: incomplete Git output");
        var errors = new ArrayList<String>();
        for (int index = 0; index < paths.size(); index++) {
            if (!paths.get(index).equals(records[index * 3]) || !"text".equals(records[index * 3 + 1]))
                throw new IllegalStateException("RELEASE-GIT-ATTR: mismatched evidence identity");
            if (!"unset".equals(records[index * 3 + 2])) errors.add(paths.get(index) + ": " + records[index * 3 + 2]);
        }
        return List.copyOf(errors);
    }

    private String git(Path root, List<String> arguments) throws Exception {
        Path output = Files.createTempFile(temporary, "git-attributes-", ".out");
        var command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(arguments);
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("RELEASE-GIT-ATTR: Git did not finish within 30 seconds");
        }
        String result = Files.readString(output);
        if (process.exitValue() != 0) throw new IllegalStateException("RELEASE-GIT-ATTR: " + result);
        return result;
    }

}

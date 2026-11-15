package atf.rentz.distribution.publication;

import atf.rentz.RentzRulePublication;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.AuditedAssertions;
import atf.testkit.assertions.ComponentAuditExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static atf.rentz.distribution.publication.GitHubPromotionQualification.*;

@AtfComponentTest
class GitHubPromotionQualificationTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT = RentzRulePublication.scoped(
            GitHubPromotionQualificationTest.class, "promotion-channel-controls.html", "Offline checks for the explicit GitHub permission probe");
    private static final String REPOSITORY = "owner/qualification-private";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void credentialsForOneAccountDoNotBecomeIndependentOperators() throws Exception {
        var first = new Identity(10, "writer-a", 88, true, true);
        var second = new Identity(11, "writer-b", 88, true, true);
        var reader = new Identity(12, "reader", 88, true, false);
        validateIdentities(first, second, reader);
        AuditedAssertions.assertThrows(IOException.class, () -> validateIdentities(first, first, reader), "Reject reuse of the same account");
        AuditedAssertions.assertThrows(IOException.class, () -> validateIdentities(first, second, second), "The writer cannot pose as reader");
        var writerInstead = new Identity(12, "reader", 88, true, true);
        AuditedAssertions.assertThrows(IOException.class, () -> validateIdentities(first, second, writerInstead), "Reject a reader with push access");
        var otherChannel = new Identity(12, "reader", 89, true, false);
        AuditedAssertions.assertThrows(IOException.class, () -> validateIdentities(first, second, otherChannel), "All roles must resolve the same GitHub repository ID");
    }

    @Test void githubResponseMustProvePrivateRepositoryAndExplicitPermissions() throws Exception {
        var user = JSON.readTree("{\"id\":10,\"login\":\"test-reader\",\"type\":\"User\"}");
        var repo = JSON.createObjectNode().put("full_name", REPOSITORY).put("private", true).put("id", 88);
        repo.putObject("permissions").put("pull", true).put("push", false);
        AuditedAssertions.assertEquals(new Identity(10, "test-reader", 88, true, false),
                identity(REPOSITORY, user, repo), "Read exact GitHub account and permission fields");
        AuditedAssertions.assertThrows(IOException.class, () -> identity(REPOSITORY, user, repo.deepCopy().put("private", false)),
                "A public repository is refused");
        var missing = repo.deepCopy();
        missing.remove("permissions");
        AuditedAssertions.assertThrows(IOException.class, () -> identity(REPOSITORY, user, missing), "Absent role data cannot qualify");
    }

    @Test void realDistributionIsOutsideThePermissionProbe() throws Exception {
        AuditedAssertions.assertEquals(REPOSITORY, testRepository(REPOSITORY), "Accept an explicit isolated repository");
        for (String target : new String[]{"owner/ATF_j5", "owner/atf_j5_releases", "owner/..", "owner/test.git", "https://github.com/" + REPOSITORY}) {
            AuditedAssertions.assertThrows(IOException.class, () -> testRepository(target), "Reject production or ambiguous target " + target);
        }
    }

    @Test void gitSubprocessUsesOnlyTheSuppliedCredentialAndRepositoryScope() {
        String remote = "https://github.com/" + REPOSITORY + ".git";
        var environment = new HashMap<>(Map.of("GIT_CONFIG_COUNT", "9", "GIT_CONFIG_PARAMETERS", "unrelated",
                "GIT_TRACE_CURL", "1", "GIT_ASKPASS", "other-helper", "GIT_DIR", "another-repo",
                "GH_TOKEN", "fixture-ambient-secret", "RENTZ_PROMOTION_WRITER_B", "fixture-other-writer"));
        isolateGitCredentials(environment, remote, "Basic fixture-header");
        AuditedAssertions.assertEquals("http." + remote + ".extraHeader", environment.get("GIT_CONFIG_KEY_2"),
                "Restrict the authorization header to the test repository");
        AuditedAssertions.assertEquals("credential.helper", environment.get("GIT_CONFIG_KEY_0"), "Reset existing helpers");
        AuditedAssertions.assertEquals("", environment.get("GIT_CONFIG_VALUE_0"), "Disable implicit credential lookup");
        AuditedAssertions.assertEquals("false", environment.get("GIT_CONFIG_VALUE_3"), "Disable HTTP redirects");
        AuditedAssertions.assertTrue(!environment.containsKey("GIT_TRACE_CURL") && !environment.containsKey("GIT_DIR")
                && !environment.containsKey("GH_TOKEN") && !environment.containsKey("RENTZ_PROMOTION_WRITER_B")
                && !environment.containsKey("GIT_CONFIG_PARAMETERS") && !environment.containsKey("GIT_ASKPASS"),
                "Discard inherited credentials, Git lookup overrides and tracing");
    }
}

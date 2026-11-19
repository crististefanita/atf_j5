package atf.rentz.distribution.publication;

import atf.rentz.RentzRulePublication;
import atf.rentz.distribution.retirement.ReleaseRetirement;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.ComponentAuditExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import static atf.testkit.assertions.AuditedAssertions.*;

@AtfComponentTest
class GitHubRetirementAdapterTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT = RentzRulePublication.scoped(
            GitHubRetirementAdapterTest.class, "retirement-adapter.html", "Loopback GitHub API and injected channel; no external writes");
    @TempDir Path output;
    static final String SOURCE = "a".repeat(40), MAIN = "b".repeat(40), OWNER = "c".repeat(40);
    static final ReleaseRetirement.Plan PLAN = new ReleaseRetirement.Plan("example/rentz", "0.29.0", SOURCE, MAIN,
            29, "https://github.com/example/rentz/releases/download/v0.29.0/RentzLab-0.29.0.apk");

    @Test void productionAdapterArchivesMetadataBeforeDeleteAndConfirmsAbsenceUnderThePublicationChannel() throws Exception {
        var removed = new AtomicBoolean(); var archivedBeforeDelete = new AtomicBoolean(); var channel = new Channel();
        var requests = new CopyOnWriteArrayList<String>(); var json = new ObjectMapper();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath().substring("/repos/example/rentz".length());
            requests.add(exchange.getRequestMethod() + " " + path);
            if (exchange.getRequestMethod().equals("DELETE")) {
                archivedBeforeDelete.set(Files.isRegularFile(output.resolve("release-28.json")) && OWNER.equals(channel.owner));
                removed.set(true); exchange.sendResponseHeaders(204, -1); exchange.close(); return;
            }
            Object body = switch(path) {
                case "" -> Map.of("full_name","example/rentz","private",false,"visibility","public","permissions",Map.of("push",true));
                case "/branches/main" -> Map.of("commit",Map.of("sha",MAIN));
                case "/git/ref/tags/v0.29.0", "/git/ref/tags/v0.28.0" -> Map.of("ref", "refs/tags/" + path.substring(path.lastIndexOf('/')+1), "object",Map.of("type","commit","sha",SOURCE));
                case "/releases/latest" -> release(29);
                case "/releases" -> removed.get() ? List.of(release(29)) : List.of(release(28),release(29));
                case "/releases/28" -> release(28);
                case "/releases/28/assets" -> List.of();
                case "/contents/README.md" -> {
                    byte[] readme = ("# Rentz\n\n## Jocul\n\n- [APK]("+PLAN.apkUrl()+")\n- [Online](https://crististefanita.github.io/rentz/)\n\n## Documentație\n").getBytes(StandardCharsets.UTF_8);
                    yield Map.of("encoding","base64","size",readme.length,"content",Base64.getEncoder().encodeToString(readme));
                }
                default -> throw new IOException("Unexpected endpoint: " + path);
            };
            byte[] bytes = json.writeValueAsBytes(body); exchange.sendResponseHeaders(200,bytes.length);
            try(var stream=exchange.getResponseBody()){stream.write(bytes);} finally{exchange.close();}
        });
        server.start();
        try {
            var endpoint = java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
            var github = new GitHubPublication(output,"offline_mock",endpoint,endpoint,ignored->{throw new IOException("No publication requested");});
            var adapter = new GitHubRetirement(github,channel,output,PLAN);
            assertEquals(List.of(28L),ReleaseRetirement.retire(PLAN,adapter),"Only the old stable record is removed");
            assertTrue(archivedBeforeDelete.get(),"Durable metadata and channel ownership precede DELETE");
            var archive = json.readTree(output.resolve("release-28.json").toFile());
            assertEquals(SOURCE,archive.path("preservedTagSource").asText(),"Source tag is archived and never deleted");
            assertEquals(28,archive.path("release").path("id").asInt(),"Metadata identifies the removed edition");
            assertEquals(List.of("DELETE /releases/28"),requests.stream().filter(row->row.startsWith("DELETE")).toList(),"No tag writes or deletion retries");
            assertEquals(null,channel.owner,"Confirmed response releases only the acquired channel");
        } finally { server.stop(0); }
    }
    private static Map<String,Object> release(int id) {
        return Map.of("id",id,"tag_name","v0."+id+".0","target_commitish",SOURCE,"draft",false,"prerelease",false);
    }
    private static final class Channel implements GitHubRetirement.Channel {
        String owner;
        public String owner(){return owner;}
        public ReleasePublication.PromotionLease reserve(String tag) throws IOException {
            if(owner!=null)throw new IOException("PUBLICATION-CHANNEL-IN-USE"); owner=OWNER;
            return new ReleasePublication.PromotionLease(OWNER,()->{if(!OWNER.equals(owner))throw new IOException("PUBLICATION-CHANNEL-OWNER");owner=null;});
        }
    }
}

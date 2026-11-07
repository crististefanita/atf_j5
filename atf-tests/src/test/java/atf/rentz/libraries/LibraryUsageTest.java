package atf.rentz.libraries;

import atf.testkit.AtfIntegrationTest;

import atf.core.http.request.*;
import atf.core.json.JsonDocuments;
import atf.console.*;
import atf.mock.AtfMockTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@AtfIntegrationTest
class LibraryUsageTest {
    @Test void physicalCoreKeepsExactJsonAndRejectsAmbiguousDocuments()throws Exception{
        assertEquals("https://example.test/api/orders/42",new ApiUrlBuilder(URI.create("https://example.test/api")).build(ApiRequest.of("GET","/orders/42")).toString());
        assertEquals(0,new BigDecimal("19.90").compareTo(JsonDocuments.read("{\"total\":19.90}").path("total").decimalValue()));
        assertThrows(Exception.class,()->JsonDocuments.read("{\"n\":1,\"n\":2}"));assertThrows(Exception.class,()->JsonDocuments.read("{} {}"));
    }
    @Test void physicalConsoleHonorsEnabledAndDisabledPolicies(){
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes, true,java.nio.charset.StandardCharsets.UTF_8);
        new AtfConsoleReporter(stream,new ConsoleSettings(true,false)).info("Mesaj ș ț");assertTrue(bytes.toString(java.nio.charset.StandardCharsets.UTF_8).contains("Mesaj ș ț"));
        bytes.reset();new AtfConsoleReporter(stream,ConsoleSettings.disabled()).info("hidden");assertEquals("",bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void physicalMockUsesOnlyItsExplicitOfflineCatalog(@TempDir Path folder)throws Exception{
        Path catalog=folder.resolve("mock.yaml");Files.writeString(catalog,"mock:\n  routes:\n    - method: GET\n      path: /health\n      status: 200\n      body: '{\"ready\":true}'\n  fallback: {status: 500, body: '{}'}\n");
        var mock=AtfMockTransport.fromConfiguration(catalog);
        assertEquals(200,mock.send(ApiRequest.of("GET","/health"),"https://offline.test/health","none").join().status());
        assertEquals(500,mock.send(ApiRequest.of("GET","/missing"),"https://offline.test/missing","none").join().status());
    }
}

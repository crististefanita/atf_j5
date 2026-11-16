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
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
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
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes, true,StandardCharsets.UTF_8);
        new AtfConsoleReporter(stream,new ConsoleSettings(true,false)).info("Mesaj ș ț");assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Mesaj ș ț"));
        bytes.reset();new AtfConsoleReporter(stream,ConsoleSettings.disabled()).info("hidden");assertEquals("",bytes.toString(StandardCharsets.UTF_8));
    }
    @Test void physicalMockUsesOnlyItsExplicitOfflineCatalog(@TempDir Path folder)throws Exception{
        Path catalog=folder.resolve("mock.yaml");Files.writeString(catalog,"mock:\n  routes:\n    - method: GET\n      path: /health\n      status: 200\n      body: '{\"ready\":true}'\n  fallback: {status: 500, body: '{}'}\n");
        var mock=AtfMockTransport.fromConfiguration(catalog);
        assertEquals(200,mock.send(ApiRequest.of("GET","/health"),"https://offline.test/health","none").join().status());
        assertEquals(500,mock.send(ApiRequest.of("GET","/missing"),"https://offline.test/missing","none").join().status());
    }


    @Test void documentedKitExampleRunsFromThePublishedJarWithoutRentzOrReporting(@TempDir Path folder) throws Exception {
        Path root = LibraryAdoptionTest.root();
        var kit = LibraryAdoption.inspect(root).stream()
                .filter(pair -> pair.coordinate().contains(":atf-test-kit:")).findFirst().orElseThrow();
        String guide = Files.readString(root.resolve("lib/README.TestKit.md"));
        var example = Pattern.compile(
                "(?s)## Exemplu minim\\R.*?```java\\R(import [^;\\r\\n]+;)\\s*\\R(.*?)\\R" + "```").matcher(guide);
        assertTrue(example.find(), "The published guide must expose one runnable Java example");
        assertEquals("import atf.testkit.assertions.RecordedAssertions;", example.group(1));
        Path source = folder.resolve("KitReadmeConsumer.java");
        Files.writeString(source, example.group(1) + "\npublic final class KitReadmeConsumer {\n"
                + example.group(2) + "\n}\n");

        var dependencies = new ArrayList<Path>();
        dependencies.add(kit.jar());
        for (Class<?> dependency : List.of(org.junit.jupiter.api.Assertions.class,
                org.opentest4j.AssertionFailedError.class, org.apiguardian.api.API.class,
                org.junit.platform.commons.JUnitException.class)) {
            dependencies.add(Path.of(dependency.getProtectionDomain().getCodeSource().getLocation().toURI()));
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK required for the standalone documentation probe");
        var diagnostics = new ByteArrayOutputStream();
        String classpath = String.join(File.pathSeparator, dependencies.stream().map(Path::toString).toList());
        int exit = compiler.run(null, diagnostics, diagnostics, "-proc:none", "-encoding", "UTF-8",
                "-classpath", classpath, "-d", folder.toString(), source.toString());
        assertEquals(0, exit, diagnostics.toString(StandardCharsets.UTF_8));

        var urls = new ArrayList<URL>();
        urls.add(folder.toUri().toURL());
        for (Path dependency : dependencies) urls.add(dependency.toUri().toURL());
        try (var loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            var recorderType = loader.loadClass("atf.testkit.assertions.RecordedAssertions");
            var exampleType = loader.loadClass("KitReadmeConsumer");
            var verify = exampleType.getMethod("verifyState", recorderType, String.class);
            for (String actual : List.of("ready", "not ready")) {
                boolean expectedPass = actual.equals("ready");
                var recorder = recorderType.getConstructor().newInstance();
                if (expectedPass) verify.invoke(null, recorder, actual);
                else {
                    var failure = assertThrows(InvocationTargetException.class,
                            () -> verify.invoke(null, recorder, actual));
                    assertEquals("org.opentest4j.AssertionFailedError", failure.getCause().getClass().getName());
                }
                var checks = (List<?>) recorderType.getMethod("checks").invoke(recorder);
                assertEquals(1, checks.size());
                var check = checks.getFirst();
                assertEquals("State", check.getClass().getMethod("name").invoke(check));
                assertEquals("ready", check.getClass().getMethod("expected").invoke(check));
                assertEquals(actual, check.getClass().getMethod("actual").invoke(check));
                assertEquals(expectedPass, check.getClass().getMethod("passed").invoke(check));
            }
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass(LibraryUsageTest.class.getName()));
        }
    }

}

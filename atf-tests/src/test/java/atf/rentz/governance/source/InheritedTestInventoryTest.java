package atf.rentz.governance.source;

import atf.rentz.discovery.TestSourceInventory;
import atf.testkit.AtfComponentTest;
import atf.testkit.AtfUnitTest;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class InheritedTestInventoryTest {
    @TempDir Path root;
    static final String SOURCE = """
            package probe;
            import atf.testkit.*;
            import org.junit.jupiter.api.Test;
            @AtfComponentTest abstract class Base { @Test void inherited() {} }
            class Leaf extends Base {}
            class OverrideWithoutAnnotation extends Base { @Override void inherited() {} }
            class OverrideWithAnnotation extends Base { @Override @Test void inherited() {} }
            @AtfUnitTest interface Defaults { @Test default void contract() {} }
            class InterfaceLeaf implements Defaults {}
            class InterfaceOverride implements Defaults { public void contract() {} }
            class InterfaceViaClass extends InterfaceLeaf {}
            interface DiamondLeft extends Defaults {}
            interface DiamondRight extends Defaults {}
            class Diamond implements DiamondLeft, DiamondRight {}
            @AtfUnitTest interface AbstractContract { @Test void contract(); }
            class ImplementedContract implements AbstractContract { public void contract() {} }
            @AtfComponentTest abstract class GenericBase<T> { @Test void generic(T value) {} }
            class GenericOverride extends GenericBase<String> { @Override void generic(String value) {} }
            class Overload extends Base { void inherited(int number) {} }
            @AtfUnitTest class ConflictingMarker extends Base {}
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            @AtfComponentTest @interface Composed {}
            @Composed abstract class ComposedBase { @Test void body() {} }
            class ComposedLeaf extends ComposedBase {}
            @java.lang.annotation.Inherited
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            @AtfComponentTest @interface InheritedComposed {}
            @InheritedComposed abstract class InheritedComposedBase { @Test void body() {} }
            class InheritedComposedLeaf extends InheritedComposedBase {}
            abstract class NestedBase {
                @org.junit.jupiter.api.Nested class Inner { @Test void body() {} }
            }
            class NestedLeaf extends NestedBase {}
            abstract class NestedMiddle extends NestedBase {}
            class TransitiveNestedTest extends NestedMiddle {}
            class ShadowedNested extends NestedMiddle { class Inner {} }
            class StaticNestedHelper {
                @org.junit.jupiter.api.Nested static class Inner { @Test void body() {} }
            }
            class ImplicitlyStaticNestedHelper {
                @org.junit.jupiter.api.Nested record Inner() { @Test void body() {} }
                @org.junit.jupiter.api.Nested enum Choice { FIRST; @Test void body() {} }
            }
            class PlainHelper {}
            """;

    @Test void inheritedMethodsOverridesInterfacesAndLayersMatchTheRealEngine() throws Exception {
        Path sources = Files.createDirectories(root.resolve("sources/probe"));
        Path file = Files.writeString(sources.resolve("Cases.java"), SOURCE);
        compare(root.resolve("sources"), List.of(file), List.of("Leaf", "OverrideWithoutAnnotation", "OverrideWithAnnotation",
                "InterfaceLeaf", "InterfaceOverride", "InterfaceViaClass", "Diamond", "ImplementedContract", "GenericOverride",
                "Overload", "ConflictingMarker", "ComposedLeaf", "InheritedComposedLeaf", "NestedLeaf",
                "TransitiveNestedTest", "ShadowedNested", "StaticNestedHelper", "ImplicitlyStaticNestedHelper", "PlainHelper")
                .stream().map(name -> "probe." + name).toList());
    }

    private void compare(Path sources, List<Path> files, List<String> names) throws Exception {
        Path classes = Files.createDirectory(root.resolve("classes"));
        var arguments = new java.util.ArrayList<>(List.of("-proc:none", "-encoding", "UTF-8",
                "-classpath", System.getProperty("java.class.path"), "-d", classes.toString()));
        files.forEach(file -> arguments.add(file.toString()));
        int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new));
        assertEquals(0, exit, "The oracle fixtures must compile");
        var inventory = TestSourceInventory.scan(root, List.of(sources));
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            assertAll(names.stream().map(name -> (org.junit.jupiter.api.function.Executable) () -> {
                        Class<?> fixture = Class.forName(name, false, loader);
                        var listener = new SummaryGeneratingListener();
                        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                                .selectors(DiscoverySelectors.selectClass(fixture)).build(), listener);
                        long actual = listener.getSummary().getTestsFoundCount();
                        var entry = inventory.stream().filter(e -> e.name().equals(fixture.getName())).findFirst().orElseThrow();
                        assertEquals(actual > 0, entry.executable(), name + ": source and engine differ");
                        var layers = new java.util.ArrayList<String>();
                        if (AnnotationSupport.findAnnotation(fixture, AtfComponentTest.class).isPresent()) layers.add(AtfComponentTest.class.getName());
                        if (AnnotationSupport.findAnnotation(fixture, AtfUnitTest.class).isPresent()) layers.add(AtfUnitTest.class.getName());
                        assertEquals(layers.stream().sorted().toList(), entry.layers().stream().sorted().toList(),
                                name + ": actual inherited/composed layer contract differs");
                    }));
        }
    }

    @Test void packageVisibilityAndPrivateMethodsAreConfrontedWithJupiter() throws Exception {
        Path sources = Files.createDirectories(root.resolve("sources"));
        Path base = Files.createDirectories(sources.resolve("base"));
        Path child = Files.createDirectories(sources.resolve("child"));
        var files = List.of(
                Files.writeString(base.resolve("Parent.java"), "package base; public abstract class Parent { @org.junit.jupiter.api.Test void inherited() {} }"),
                Files.writeString(child.resolve("Untouched.java"), "package child; public class Untouched extends base.Parent {}"),
                Files.writeString(child.resolve("SameName.java"), "package child; public class SameName extends base.Parent { public void inherited() {} }"),
                Files.writeString(child.resolve("PrivateCase.java"), "package child; public class PrivateCase { @org.junit.jupiter.api.Test private void hidden() {} }"));
        compare(sources, files, List.of("child.Untouched", "child.SameName", "child.PrivateCase"));
    }

    @Test void unresolvedExternalInheritanceCannotBecomeAnOrdinaryHelper() throws Exception {
        Path sources = Files.createDirectories(root.resolve("test/p"));
        for (String parent : List.of("extends outside.UnknownBase", "implements outside.UnknownInterface")) {
            Files.writeString(sources.resolve("LostSpec.java"), "package p; class LostSpec " + parent + " {}");
            assertThrows(IllegalArgumentException.class, () -> TestSourceInventory.scan(root, List.of(root.resolve("test"))),
                    "Unknown inheritance must be an explicit refusal rather than metadata");
        }
        Files.writeString(sources.resolve("LostSpec.java"), "package p; class LostSpec extends Base {} class Base extends LostSpec {}");
        assertThrows(IllegalArgumentException.class, () -> TestSourceInventory.scan(root, List.of(root.resolve("test"))));
    }

    @Test void reviewedExternalInterfacesExposeNoTestsAndLayerTerminalInheritanceIsExplicit() throws Exception {
        var layout = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(GovernanceProject.root().resolve("config/java-source-layout.json").toFile());
        for (var external : layout.required("externalNonTestBases")) {
            String binary = external.asText();
            Class<?> type = null;
            while (type == null) {
                try { type = Class.forName(binary, false, getClass().getClassLoader()); }
                catch (ClassNotFoundException missing) {
                    int split = binary.lastIndexOf('.');
                    if (split < 0) throw missing;
                    binary = binary.substring(0, split) + "$" + binary.substring(split + 1);
                }
            }
            assertTrue(type.isInterface(), "External exceptions are reviewed interfaces, not opaque test base classes");
            assertTrue(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString().endsWith(".jar"),
                    "A source-directory shadow must not replace the reviewed dependency interface");
            for (var method : type.getMethods()) {
                assertFalse(AnnotationSupport.isAnnotated(method, Test.class));
                assertFalse(AnnotationSupport.isAnnotated(method, org.junit.jupiter.api.TestTemplate.class));
                assertFalse(AnnotationSupport.isAnnotated(method, org.junit.jupiter.api.TestFactory.class));
                assertTrue(java.util.Arrays.stream(method.getAnnotations()).noneMatch(a -> a.annotationType().getName().equals("org.junit.Test")));
            }
        }
        for (var marker : List.of(AtfComponentTest.class, AtfUnitTest.class,
                atf.testkit.AtfIntegrationTest.class, atf.testkit.AtfE2eTest.class))
            assertFalse(marker.isAnnotationPresent(java.lang.annotation.Inherited.class),
                    "A changed Kit inheritance contract requires an inventory adapter update");
    }
}

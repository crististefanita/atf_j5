package atf.rentz.governance;

import atf.rentz.PolicyInputs;
import atf.rentz.RuleCatalog;
import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class RuleBindingGovernanceTest {
    @TempDir Path project;

    private Path source(String text) throws Exception {
        Path file = project.resolve("rentz-lab-app/game-core/src/test/java/ro/rentzlab/core/BindingExampleTest.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }

    private static String unit(String imports, String members) {
        return "package ro.rentzlab.core;\n" + imports + "\nclass BindingExampleTest {\n" + members + "\n}";
    }

    private void verify() throws Exception {
        RuleCatalog.verifyBindings(project, List.of(new RuleCatalog.Claim(
                "BINDING-CONTROL", "LAB_CONTRACT", "AUDIT", "RentzMatch.choose", "BindingExampleTest.verify")));
    }

    static Stream<String> nonTests() {
        String junit = "import org.junit.jupiter.api.Test;";
        return Stream.of(
                unit(junit, "// @Test void verify() {}"),
                unit(junit, "String sample = \"@Test void verify() {}\";"),
                unit(junit, "String sample = \"\"\"\n@Test void verify() {}\n\"\"\";"),
                unit(junit, "void verify() {}"),
                unit("import example.Test;", "@Test void verify() {}"),
                unit("import static example.Markers.Test; import org.junit.jupiter.api.*;", "@Test void verify() {}"),
                unit("import example.org;", "@org.junit.jupiter.api.Test void verify() {}"),
                unit("import static example.Markers.*; import org.junit.jupiter.api.*;", "@Test void verify() {}"),
                unit("", "@example.Test void verify() {}"),
                unit("", "@Test void verify() {}"),
                unit(junit, "class Nested { @Test void verify() {} }"),
                unit(junit, "@interface Test {} @Test void verify() {}"),
                unit(junit, "@Test private void verify() {}"),
                unit(junit, "@Test static void verify() {}"),
                unit(junit, "@Test abstract void verify();"),
                unit(junit, "@Test int verify() { return 0; }"),
                unit(junit, "@Test void verify() {} @Test void verify(int input) {}"),
                unit("import org.junit.jupiter.api.*; import example.*;", "@Test void verify() {}"),
                unit(junit, "@Test void verify() { broken syntax ! }"),
                "package wrong; import org.junit.jupiter.api.Test; class BindingExampleTest { @Test void verify() {} }",
                "package ro.rentzlab.core; import org.junit.jupiter.api.Test; class AnotherTest { @Test void verify() {} }");
    }

    @ParameterizedTest @MethodSource("nonTests")
    void yamlCannotBindExamplesHelpersForeignAnnotationsOrWrongOwners(String text) throws Exception {
        source(text);
        assertThrows(IllegalArgumentException.class, this::verify, text);
    }

    static Stream<String> validTests() {
        return Stream.of(
                unit("import org.junit.jupiter.api.Test;", "@Test void verify() {}"),
                unit("import org.junit.jupiter.api.Test;", "public @Test void verify() {}"),
                unit("", "@org.junit.jupiter.api.Test void verify() {}"),
                unit("import org.junit.jupiter.params.ParameterizedTest;", "@ParameterizedTest void verify(int input) {}"),
                unit("", "@org.junit.jupiter.params.ParameterizedTest void verify(String input) {}"),
                unit("import org.junit.jupiter.api.*;", "@Test void verify() {}"),
                unit("import org.junit.jupiter.api.*; import static org.junit.jupiter.api.Assertions.*;", "@Test void verify() {}"),
                unit("import org.junit.jupiter.api.Test; import example.*;", "@Test void verify() {}"),
                unit("import org.junit.jupiter.api.Test;", "@Test vo" + (char)92 + "u0069d verify() {}"));
    }

    @ParameterizedTest @MethodSource("validTests")
    void realJupiterAndParameterizedDeclarationsKeepTheirLegalForms(String text) throws Exception {
        source(text);
        assertDoesNotThrow(this::verify, text);
    }

    @Test void diagnosticIdentifiesRuleFileAndMethod() throws Exception {
        Path file = source(unit("", "void verify() {}"));
        var failure = assertThrows(IllegalArgumentException.class, this::verify);
        assertAll(
                () -> assertTrue(failure.getMessage().contains("BINDING-CONTROL")),
                () -> assertTrue(failure.getMessage().contains(file.getFileName().toString())),
                () -> assertTrue(failure.getMessage().contains("verify")));
    }

    @Test void allCurrentRuleAndPolicyManifestsResolveEligibleDeclarations() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("config/rentz-rules.yaml"))) root = root.getParent();
        assertNotNull(root);
        RuleCatalog.verifyBindings(root, RuleCatalog.claims(root.resolve("config/rentz-rules.yaml")));
        for (String name : List.of("rentz-policy.yaml", "rentz-hard-policy.yaml", "rentz-contextual-policy.yaml", "rentz-match-policy.yaml"))
            PolicyInputs.read(root.resolve("config/" + name), root);
    }
}

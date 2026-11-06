package atf.testkit.config;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfUnitTest
class AtfTestKitConfigurationLoaderTest {
    @TempDir
    Path temp;

    @Test
    void loadsRelativeRootsAndExactPlugAndPlaySelections() throws Exception {
        Path config = temp.resolve("atf-test-kit.yaml");
        Files.writeString(config, """
                testKit:
                  sourceRoots: [atf-tests/src/test/java]
                  resourceRoots: [atf-tests/src/test/resources]
                  plans:
                    selected-api:
                      - atf.api.tests.UsersApiScenarios#readUser
                      - atf.api.tests.PostsApiScenarios#createPost
                """);

        var loaded = AtfTestKitConfigurationLoader.load(config);

        assertEquals(Path.of("atf-tests/src/test/java"), loaded.sourceRoots().getFirst());
        assertEquals(2, loaded.selections("selected_api").size());
        assertEquals("createPost", loaded.selections("selected-api").getLast().methodName());
        assertEquals(temp.resolve("atf-tests/src/test/java").toAbsolutePath().normalize(),
                loaded.resolve(loaded.sourceRoots().getFirst()));
    }

    @Test
    void rejectsAbsoluteConfiguredRoots() throws Exception {
        Path config = temp.resolve("atf-test-kit.yaml");
        Files.writeString(config, "testKit:\n  sourceRoots: [C:/outside]\n");

        assertThrows(IllegalStateException.class, () -> AtfTestKitConfigurationLoader.load(config));
    }

    @Test
    void discoversTheSharedAtfYamlWhenItContainsTheOptionalTestKitSection() throws Exception {
        Path project = Files.createDirectories(temp.resolve("project"));
        Path nested = Files.createDirectories(project.resolve("module/target"));
        Files.writeString(project.resolve("atf.yaml"), """
                testKit:
                  plans:
                    smoke:
                      - atf.api.tests.UsersApiScenarios#readUser
                """);

        var loaded = AtfTestKitConfigurationLoader.load(
                new AtfTestKitConfigurationContext(nested, null, null));

        assertEquals(1, loaded.selections("smoke").size());
        assertEquals(project.resolve("atf.yaml").toAbsolutePath().normalize(), loaded.configFile());
    }
}

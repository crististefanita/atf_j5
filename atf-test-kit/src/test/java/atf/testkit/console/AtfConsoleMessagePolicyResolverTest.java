package atf.testkit.console;

import atf.testkit.AtfConsoleMessages;
import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfUnitTest
class AtfConsoleMessagePolicyResolverTest {
    @Test
    void defaultsToInheritedConfigurationWhenNoDeclarationExists() {
        assertEquals(AtfConsoleMessages.Policy.INHERIT,
                AtfConsoleMessagePolicyResolver.resolve(Unconfigured.class));
    }

    @Test
    void readsDirectAndMetaDeclarations() {
        assertEquals(AtfConsoleMessages.Policy.DETAILS,
                AtfConsoleMessagePolicyResolver.resolve(DirectDetails.class));
        assertEquals(AtfConsoleMessages.Policy.SUMMARY,
                AtfConsoleMessagePolicyResolver.resolve(MetaSummary.class));
    }

    @Test
    void nearestSuperclassDeclarationIsInheritedAndCanBeOverridden() {
        assertEquals(AtfConsoleMessages.Policy.DETAILS,
                AtfConsoleMessagePolicyResolver.resolve(InheritedDetails.class));
        assertEquals(AtfConsoleMessages.Policy.OFF,
                AtfConsoleMessagePolicyResolver.resolve(OverrideOff.class));
    }

    @Test
    void rejectsConflictingDeclarationsAtTheSameClassLevel() {
        assertThrows(IllegalStateException.class,
                () -> AtfConsoleMessagePolicyResolver.resolve(Conflicting.class));
    }

    private static class Unconfigured { }

    @AtfConsoleMessages(AtfConsoleMessages.Policy.DETAILS)
    private static class DirectDetails { }

    @SummaryConsole
    private static class MetaSummary { }

    @AtfConsoleMessages(AtfConsoleMessages.Policy.DETAILS)
    private static class ParentDetails { }

    private static class InheritedDetails extends ParentDetails { }

    @AtfConsoleMessages(AtfConsoleMessages.Policy.OFF)
    private static class OverrideOff extends ParentDetails { }

    @SummaryConsole
    @AtfConsoleMessages(AtfConsoleMessages.Policy.DETAILS)
    private static class Conflicting { }

    @AtfConsoleMessages(AtfConsoleMessages.Policy.SUMMARY)
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface SummaryConsole { }
}

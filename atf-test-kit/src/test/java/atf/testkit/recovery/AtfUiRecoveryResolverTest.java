package atf.testkit.recovery;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;
import java.lang.annotation.*;
import static atf.testkit.recovery.AtfUiRecovery.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

@AtfUnitTest
class AtfUiRecoveryResolverTest {
    @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @AtfUiRecovery(READ_ONLY) @interface ReadOnly { }
    @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.METHOD})
    @AtfUiRecovery(DISABLED) @interface Off { }
    @AtfUiRecovery(DISABLED) static class Parent { public void inherited() { } }
    static class Child extends Parent { @AtfUiRecovery(READ_ONLY) public void body() { } }
    @ReadOnly static class Nearest extends Parent { @Off public void body() { } }
    @AtfUiRecovery static class Reset extends Parent { public void body() { } }
    @ReadOnly @Off static class Conflict { public void body() { } }
    @ReadOnly static class MethodConflict { @ReadOnly @Off public void body() { } }
    static class Plain { public void body() { } }

    @Test void absentDeclarationIsConfiguredAndCarriesNoActivationSignal() throws Exception {
        var plan = AtfUiRecoveryResolver.resolve(Plain.class, Plain.class.getMethod("body"));
        assertEquals(CONFIGURED, plan.mode()); assertEquals("YAML", plan.source());
    }
    @Test void methodCanDisableButCannotReopenADisabledClass() throws Exception {
        assertEquals(DISABLED, AtfUiRecoveryResolver.resolve(Child.class, Child.class.getMethod("body")).mode());
        assertEquals(DISABLED, AtfUiRecoveryResolver.resolve(Nearest.class, Nearest.class.getMethod("body")).mode());
    }
    @Test void nearestDeclaringClassAndComposedAnnotationsResolveBeforeResources() throws Exception {
        assertEquals(READ_ONLY, AtfUiRecoveryResolver.resolve(Nearest.class, null).mode());
        assertEquals(CONFIGURED, AtfUiRecoveryResolver.resolve(Reset.class, Reset.class.getMethod("body")).mode());
        assertEquals(DISABLED, AtfUiRecoveryResolver.resolve(Child.class, Parent.class.getMethod("inherited")).mode());
    }
    @Test void conflictsAtTheSameLevelAreRejectedRatherThanSelectedByReflectionOrder() throws Exception {
        assertThrows(IllegalStateException.class, () -> AtfUiRecoveryResolver.resolve(Conflict.class, null));
        var method = MethodConflict.class.getMethod("body");
        assertThrows(IllegalStateException.class, () -> AtfUiRecoveryResolver.resolve(MethodConflict.class, method));
    }
    @Test void anUnrelatedMethodCannotSupplyAnotherInvocationsPolicy() throws Exception {
        var method = Plain.class.getMethod("body");
        assertThrows(IllegalArgumentException.class, () -> AtfUiRecoveryResolver.resolve(Child.class, method));
    }
    @Test void repeatedAndParallelResolutionDoesNotShareEffectiveState() throws Exception {
        var tasks = java.util.stream.IntStream.range(0, 20).mapToObj(index -> java.util.concurrent.CompletableFuture.supplyAsync(
                () -> AtfUiRecoveryResolver.resolve(index % 2 == 0 ? Nearest.class : Child.class, null).mode())).toList();
        for (int index = 0; index < tasks.size(); index++) assertEquals(index % 2 == 0 ? READ_ONLY : DISABLED, tasks.get(index).join());
    }
}

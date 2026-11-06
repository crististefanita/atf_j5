package atf.testkit.channel;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static atf.testkit.channel.TestChannel.API;
import static atf.testkit.channel.TestChannel.UI;
import static atf.testkit.channel.TestChannel.UI_API;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfUnitTest
class AtfTestChannelResolverTest {
    @AtfTestChannel(UI) private static class UiOwner { }
    @AtfTestChannel(API) private static class ApiOwner { }
    @AtfTestChannel(UI_API) private static class CombinedOwner { }
    @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
    @AtfTestChannel(API) private @interface ApiComponent { }
    @ApiComponent private static class ComposedOwner { }
    private static class InheritedOwner extends UiOwner { }
    @ApiComponent @AtfTestChannel(API) private static class EquivalentOwner { }
    @AtfTestChannel(UI_API) private static class OverrideOwner extends UiOwner { }
    @ApiComponent @AtfTestChannel(UI) private static class ConflictOwner { }
    private static class UnclassifiedOwner { }
    @AtfTestChannel(UI) private interface UiContract { }
    private static class InterfaceOwner implements UiContract { }
    @AtfTestChannel(API) private static class EnclosingOwner {
        private static class NestedOwner { }
    }
    @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
    @CycleB private @interface CycleA { }
    @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
    @CycleA @AtfTestChannel(API) private @interface CycleB { }
    @CycleA private static class CyclicMetaOwner { }

    @Test
    void resolvesAllChannelsAndNearestDeclaration() {
        assertEquals(UI, AtfTestChannelResolver.resolve(UiOwner.class).orElseThrow().channel());
        assertEquals(API, AtfTestChannelResolver.resolve(ApiOwner.class).orElseThrow().channel());
        assertEquals(UI_API, AtfTestChannelResolver.resolve(CombinedOwner.class).orElseThrow().channel());
        assertEquals(API, AtfTestChannelResolver.resolve(ComposedOwner.class).orElseThrow().channel());
        assertEquals(UI, AtfTestChannelResolver.resolve(InheritedOwner.class).orElseThrow().channel());
        assertEquals(API, AtfTestChannelResolver.resolve(EquivalentOwner.class).orElseThrow().channel());
        assertEquals(UI_API, AtfTestChannelResolver.resolve(OverrideOwner.class).orElseThrow().channel());
        assertTrue(AtfTestChannelResolver.resolve(UnclassifiedOwner.class).isEmpty());
        assertTrue(AtfTestChannelResolver.resolve(InterfaceOwner.class).isEmpty());
        assertTrue(AtfTestChannelResolver.resolve(EnclosingOwner.NestedOwner.class).isEmpty());
        assertEquals(API, AtfTestChannelResolver.resolve(CyclicMetaOwner.class).orElseThrow().channel());
        assertThrows(IllegalStateException.class, () -> AtfTestChannelResolver.resolve(ConflictOwner.class));
    }

    @Test
    void rejectsUnsupportedChannelBeforeConsumerDoesIo() {
        AtomicInteger opened = new AtomicInteger();
        TestChannelPlan plan = AtfTestChannelResolver.resolve(ApiOwner.class).orElseThrow();
        assertThrows(IllegalStateException.class, () -> {
            plan.requireSupported(Set.of(UI, UI_API));
            opened.incrementAndGet();
        });
        assertEquals(0, opened.get());
        plan.requireSupported(Set.of(API));
        opened.incrementAndGet();
        assertEquals(1, opened.get());
    }
}

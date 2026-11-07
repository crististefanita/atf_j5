package ro.rentzlab.app.ui.collection;

import org.junit.*;
import ro.rentzlab.app.testing.*;
import java.util.Arrays;
import java.util.stream.Stream;

@AndroidComponentLayer
public final class UiListsTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    @Test public void portableCollectorPreservesOrderNullValuesAndImmutability() {
        var values = Stream.of("Ana", null, "Mara").collect(UiLists.immutable());
        audit.equal("Original ordered elements", Arrays.asList("Ana", null, "Mara"), values);
        audit.rejects("Immutable public projection", UnsupportedOperationException.class, () -> values.add("Radu"));
        audit.equal("Empty projection", java.util.List.of(), Stream.empty().collect(UiLists.immutable()));
    }
}

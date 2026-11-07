package ro.rentzlab.app.ui.collection;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/** Immutable ordered collection using Java 8 APIs available on the declared Android minimum. */
public final class UiLists {
    private UiLists() { }
    public static <T> Collector<T, ?, List<T>> immutable() {
        return Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList);
    }
}

package ro.rentzlab.app.lifecycle;

import org.junit.Test;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@AndroidComponentLayer
public final class TurnTraceTest {
    @Test public void diagnosticTraceIsSafeWithoutAndroidRuntime() {
        TurnTrace.reset();
        TurnTrace.record("component-test");
    }

    @Test public void installedSinkReceivesStagesAndCanBeReset() {
        var events = new ArrayList<String>();
        try {
            TurnTrace.install(events::add);
            TurnTrace.record("frame-visible");
            assertEquals(List.of("frame-visible"), events);
        } finally {
            TurnTrace.reset();
        }
        TurnTrace.record("after-reset");
        assertEquals(List.of("frame-visible"), events);
    }
}

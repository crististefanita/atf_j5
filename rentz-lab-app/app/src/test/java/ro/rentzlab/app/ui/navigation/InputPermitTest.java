package ro.rentzlab.app.ui.navigation;

import org.junit.*;
import ro.rentzlab.app.testing.*;

@AndroidComponentLayer
public final class InputPermitTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    @Test public void staleRouteRevisionOrBusyOwnerCannotReceiveACommand() {
        audit.equal("Old revision", false, new InputPermit(3, 7).consume(4, 7, false));
        audit.equal("Old route after navigation", false, new InputPermit(3, 7).consume(3, 8, false));
        audit.equal("Pending background work", false, new InputPermit(3, 7).consume(3, 7, true));
        audit.equal("Current idle frame", true, new InputPermit(3, 8).consume(3, 8, false));
    }
    @Test public void aConsumedFrameCannotRetryEvenWhenTheCommandFailsWithoutChangingRevision() {
        var permit = new InputPermit(3, 7);
        audit.equal("First issue", true, permit.consume(3, 7, false));
        audit.equal("Second issue against unchanged revision", false, permit.consume(3, 7, false));
        audit.equal("Fresh explicit frame", true, new InputPermit(3, 8).consume(3, 8, false));
    }
}

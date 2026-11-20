package ro.rentzlab.app.lifecycle;

import org.junit.Rule;
import org.junit.Test;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;

/** Controlled worker/delivery queues model a profile dialog interrupting an automatic turn. */
@AndroidComponentLayer
public final class ProfileModalComponentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();

    @Test public void editingNameDiscardsOldDeliveryAndResumesFromTheSameCheckpoint() throws Exception {
        try (var fixture = new LifecycleFixture(session -> session.apply(LifecycleFixture.REVEAL))) {
            var original = fixture.controller.session().save();
            String stored = fixture.storage.saved;
            fixture.controller.resume(); fixture.worker.next();
            audit.equal("The automatic result is queued before the dialog", false, fixture.delivery.tasks.isEmpty());
            fixture.controller.deactivate(); fixture.delivery.all();
            audit.equal("Opening the modal cannot apply an old move", original, fixture.controller.session().save());
            audit.equal("Opening the modal preserves the durable journal", stored, fixture.storage.saved);
            audit.equal("Old delivery does not redraw behind the dialog", 0, fixture.events.ready);
            audit.equal("Old delivery cannot report an unrelated failure", 0, fixture.events.failed);
            fixture.controller.activate();
            audit.equal("Dismiss resumes the same checkpoint", original, fixture.controller.session().save());
            fixture.controller.resume(); fixture.worker.next(); fixture.delivery.all();
            audit.equal("A fresh automatic result is delivered once", 1, fixture.events.ready);
            audit.equal("The fresh move is durable", true, fixture.storage.restored().view().revealed());
            audit.equal("The discarded move is not credited twice", 1, fixture.storage.restored().save().commands().size());
        }
    }
}

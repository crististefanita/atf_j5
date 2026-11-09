package ro.rentzlab.app.profile;

import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/** Identity and calendar belong to the local installation, independent of the display name. */
public record LocalProfile(UUID id, ZoneId zone) {
    public LocalProfile {
        Objects.requireNonNull(id);
        Objects.requireNonNull(zone);
    }
}

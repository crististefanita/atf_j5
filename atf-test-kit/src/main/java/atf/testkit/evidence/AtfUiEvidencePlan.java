package atf.testkit.evidence;

import java.util.Objects;

/** Immutable declaration; CONFIGURED inherits the corresponding consumer-owned axis. */
public record AtfUiEvidencePlan(AtfUiEvidenceFlow flow, AtfUiScreenshotPolicy screenshots,
                                AtfUiActionDetails actionDetails) {
    public AtfUiEvidencePlan {
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(screenshots, "screenshots");
        Objects.requireNonNull(actionDetails, "actionDetails");
    }

    public static AtfUiEvidencePlan configured() {
        return new AtfUiEvidencePlan(AtfUiEvidenceFlow.CONFIGURED,
                AtfUiScreenshotPolicy.CONFIGURED, AtfUiActionDetails.CONFIGURED);
    }

    public static AtfUiEvidencePlan from(AtfUiEvidence declaration) {
        return declaration == null ? configured() : new AtfUiEvidencePlan(
                declaration.flow(), declaration.screenshots(), declaration.actionDetails());
    }
}

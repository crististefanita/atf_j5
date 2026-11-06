package atf.testkit.evidence;

/** Consumer-neutral declaration of how browser checkpoints are grouped in an ATF report. */
public enum AtfUiEvidenceFlow {
    /** Leave this axis to the consumer's existing configuration. */
    CONFIGURED,
    /** Keep independently navigable screen/checkpoint nodes. */
    SCREEN_CHECKPOINTS,
    /** Consolidate the browser journey into one flow with a terminal screen. */
    UNIFIED_FLOW
}

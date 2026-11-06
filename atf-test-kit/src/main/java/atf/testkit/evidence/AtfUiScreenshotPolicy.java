package atf.testkit.evidence;

/** Consumer-neutral screenshot intent; adapters map it to their reporting implementation. */
public enum AtfUiScreenshotPolicy {
    /** Leave this axis to the consumer's existing configuration. */
    CONFIGURED,
    EACH_CHECKPOINT,
    FINAL_ONLY,
    FAILURE_ONLY,
    FINAL_AND_FAILURE,
    NONE
}

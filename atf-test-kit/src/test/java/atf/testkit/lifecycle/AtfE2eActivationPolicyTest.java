package atf.testkit.lifecycle;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfUnitTest
class AtfE2eActivationPolicyTest {
    private static final String PROPERTY = "atf.consumer.e2e.enabled";

    @Test
    void explicitJvmValueOverridesConfigurationAndIdeDetection() {
        var enabled = evaluate("true", true, true);
        var disabled = evaluate("false", true, true);

        assertTrue(enabled.enabled());
        assertEquals("enabled by JVM property " + PROPERTY + "=true", enabled.reason());
        assertFalse(disabled.enabled());
        assertEquals("disabled by explicit JVM property " + PROPERTY + "=false", disabled.reason());
    }

    @Test
    void malformedOverrideFailsClosedWithActionableReason() {
        var decision = evaluate("yes", false, true);

        assertFalse(decision.enabled());
        assertTrue(decision.reason().contains("exactly true or false"));
    }

    @Test
    void configuredOptInPrecedesIdeFallbackAndIdentifiesItsSource() {
        var configured = AtfE2eActivationPolicy.evaluate(PROPERTY, null, true,
                "consumer YAML configuration", true);
        var ide = evaluate(null, false, true);

        assertEquals("enabled by consumer YAML configuration", configured.reason());
        assertEquals("enabled by IntelliJ runner detection heuristic", ide.reason());
    }

    @Test
    void ordinaryRunnerRemainsDisabledAndNamesTheOptIn() {
        var decision = evaluate(null, false, false);

        assertFalse(decision.enabled());
        assertEquals("set -D" + PROPERTY + "=true to run public-site E2E", decision.reason());
    }

    @Test
    void configuredEnablementRequiresAnExplicitSourceLabel() {
        assertThrows(NullPointerException.class,
                () -> AtfE2eActivationPolicy.evaluate(PROPERTY, null, true, null, false));
    }

    private static AtfE2eActivationPolicy.Decision evaluate(String override,
                                                             boolean configured,
                                                             boolean ideDetected) {
        return AtfE2eActivationPolicy.evaluate(PROPERTY, override, configured,
                configured ? "consumer YAML configuration" : null, ideDetected);
    }
}

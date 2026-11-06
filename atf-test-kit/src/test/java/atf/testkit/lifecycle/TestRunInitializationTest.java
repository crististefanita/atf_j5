package atf.testkit.lifecycle;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@AtfUnitTest
class TestRunInitializationTest {
    @Test
    void successTransfersEveryHandleWithoutRollback() {
        var events = new ArrayList<String>();

        var run = create(events, ignored -> "scenario", ignored -> "phase", phase -> "run");

        assertEquals("run", run);
        assertEquals(List.of("resource", "scenario", "phase", "run"), events);
    }

    @Test
    void resourceAcquisitionFailureDoesNotInvokeFactoriesOrRollbackCallbacks() {
        var events = new ArrayList<String>();
        var failure = new IllegalStateException("resource creation failed");

        var actual = assertThrows(IllegalStateException.class, () -> TestRunInitialization.create(
                () -> { throw failure; },
                ignored -> fail("scenario must not be created"),
                ignored -> fail("phase must not be created"),
                (resource, scenario, phase) -> fail("run must not be created"),
                ignored -> events.add("phase FAIL"),
                ignored -> events.add("scenario FAIL"),
                ignored -> events.add("close resource")));

        assertSame(failure, actual);
        assertEquals(List.of(), events);
    }

    @Test
    void failureAfterResourceAcquisitionClosesOnlyTheResource() {
        var events = new ArrayList<String>();
        var failure = new IllegalStateException("scenario creation failed");

        var actual = assertThrows(IllegalStateException.class, () -> create(
                events,
                ignored -> { throw failure; },
                ignored -> fail("phase must not be created"),
                ignored -> fail("run must not be created")));

        assertSame(failure, actual);
        assertEquals(List.of("resource", "scenario", "close resource"), events);
    }

    @Test
    void failureAfterScenarioAcquisitionRollsBackScenarioThenResource() {
        var events = new ArrayList<String>();
        var failure = new IllegalArgumentException("phase creation failed");

        var actual = assertThrows(IllegalArgumentException.class, () -> create(
                events,
                ignored -> "scenario",
                ignored -> { throw failure; },
                ignored -> fail("run must not be created")));

        assertSame(failure, actual);
        assertEquals(List.of("resource", "scenario", "phase", "scenario FAIL", "close resource"), events);
    }

    @Test
    void failureAfterPhaseAcquisitionRollsBackPhaseScenarioAndResource() {
        var events = new ArrayList<String>();
        var failure = new IllegalStateException("run creation failed");

        var actual = assertThrows(IllegalStateException.class, () -> create(
                events, ignored -> "scenario", ignored -> "phase", ignored -> { throw failure; }));

        assertSame(failure, actual);
        assertEquals(List.of("resource", "scenario", "phase", "run",
                "phase FAIL", "scenario FAIL", "close resource"), events);
    }

    @Test
    void rollbackFailuresAreSuppressedWithoutReplacingTheSetupFailure() {
        var events = new ArrayList<String>();
        var setupFailure = new IllegalStateException("phase creation failed");
        var scenarioFailure = new IllegalArgumentException("scenario close failed");
        var resourceFailure = new UnsupportedOperationException("resource close failed");

        var actual = assertThrows(IllegalStateException.class, () -> TestRunInitialization.create(
                () -> { events.add("resource"); return "resource"; },
                ignored -> { events.add("scenario"); return "scenario"; },
                ignored -> { throw setupFailure; },
                (resource, scenario, phase) -> "run",
                ignored -> events.add("phase FAIL"),
                ignored -> { events.add("scenario FAIL"); throw scenarioFailure; },
                ignored -> { events.add("close resource"); throw resourceFailure; }));

        assertSame(setupFailure, actual);
        assertEquals(List.of("resource", "scenario", "scenario FAIL", "close resource"), events);
        assertEquals(List.of(scenarioFailure, resourceFailure), List.of(actual.getSuppressed()));
    }

    @Test
    void errorsAlsoRollBackEveryHandleEvenWhenFailurePublicationThrows() {
        var events = new ArrayList<String>();
        var failure = new NoClassDefFoundError("run unavailable");
        var phaseFailure = new AssertionError("phase publication failed");
        var scenarioFailure = new IllegalStateException("scenario publication failed");
        var resourceFailure = new LinkageError("resource close failed");

        var actual = assertThrows(NoClassDefFoundError.class, () -> TestRunInitialization.create(
                () -> "resource", ignored -> "scenario", ignored -> "phase",
                (resource, scenario, phase) -> { throw failure; },
                ignored -> { events.add("phase FAIL"); throw phaseFailure; },
                ignored -> { events.add("scenario FAIL"); throw scenarioFailure; },
                ignored -> { events.add("close resource"); throw resourceFailure; }));

        assertSame(failure, actual);
        assertEquals(List.of("phase FAIL", "scenario FAIL", "close resource"), events);
        assertEquals(List.of(phaseFailure, scenarioFailure, resourceFailure), List.of(actual.getSuppressed()));
    }

    private static String create(List<String> events,
                                  Function<String, String> scenarioFactory,
                                  Function<String, String> phaseFactory,
                                  Function<String, String> runFactory) {
        return TestRunInitialization.create(
                () -> { events.add("resource"); return "resource"; },
                resource -> { assertEquals("resource", resource); events.add("scenario");
                    return scenarioFactory.apply(resource); },
                scenario -> { assertEquals("scenario", scenario); events.add("phase");
                    return phaseFactory.apply(scenario); },
                (resource, scenario, phase) -> {
                    assertEquals(List.of("resource", "scenario", "phase"), List.of(resource, scenario, phase));
                    events.add("run");
                    return runFactory.apply(phase);
                },
                ignored -> events.add("phase FAIL"),
                ignored -> events.add("scenario FAIL"),
                ignored -> events.add("close resource"));
    }
}

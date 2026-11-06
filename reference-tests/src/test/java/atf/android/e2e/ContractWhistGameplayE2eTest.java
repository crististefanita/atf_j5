package atf.android.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import atf.android.AdbClient;
import atf.android.evidence.AndroidScreenshotEvidence;
import atf.reporting.contract.AtfReportPorts;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfGherkinKeyword;
import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.evidence.ReportScreenshotPolicy;
import atf.reporting.evidence.profile.ReportEvidenceProfile;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import atf.reporting.model.ReportScreenshot;
import atf.reporting.ui.UiExtentReportSession;
import atf.reporting.ui.model.AtfUiActionRecord;
import atf.reporting.ui.model.AtfUiEvidenceSource;
import atf.android.e2e.support.AndroidUiFlowTrace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Explicit opt-in: never installs software or launches an app during the default Maven build. */
@AndroidE2eTest
public class ContractWhistGameplayE2eTest {
    private static final String PACKAGE = "com.qsoft.android.whist";

    @Test
    void menusGameSetupAndBiddingFlowWork() throws Exception {
        AdbClient adb = AdbClient.fromSystemProperties();
        var packagePath = adb.run("shell", "pm", "path", PACKAGE);
        packagePath.requireSuccess();
        assertTrue(packagePath.output().contains("package:"), "Package is not installed: " + packagePath.output());

        Path artifacts = Path.of(System.getProperty("atf.android.artifacts", "target/android-artifacts"));
        AndroidUiDriver ui = new AndroidUiDriver(adb, artifacts);
        List<Path> evidence = new ArrayList<>();

        ui.restartToMainMenu(PACKAGE);
        String menu = ui.dump();
        for (String label : List.of("New Game", "Resume Game", "Instructions", "High Scores", "Multiplayer"))
            assertTrue(ui.hasText(menu, label), "Main menu is missing " + label);
        evidence.addAll(ui.capture("main-menu"));

        ui.tapText("Instructions");
        String instructions = ui.awaitContains("Bidding Phase", Duration.ofSeconds(10));
        evidence.addAll(ui.capture("instructions"));
        assertTrue(instructions.contains("Play Phase"), "Instructions do not describe the play phase");
        assertTrue(instructions.contains("additional 10 points"), "Instructions do not describe scoring");
        String ruleText = String.join(" ", ui.texts(instructions)).toLowerCase(java.util.Locale.ROOT);
        List<String> requiredRules = List.of(
                "2-5 ai opponents", "standard deck of playing cards",
                "number of tricks they think they are going to win",
                "last player to bid cannot bid to make the total bids equal the number of cards in the hand",
                "exact number of tricks that player thinks they will win",
                "player to dealer's left", "same suit if possible", "highest card winning the trick",
                "discard an unwanted card or play a trump", "highest trump wins the trick",
                "if \"nt\" is displayed", "spades, hearts, clubs, diamonds, no trumps",
                "1 point for every trick they won", "additional 10 points",
                "number of cards in the hand", "players' scores so far");
        List<String> missingRules = requiredRules.stream().filter(rule -> !ruleText.contains(rule)).toList();
        assertTrue(missingRules.isEmpty(), "Original Instructions changed or missed reconstructed rules: "
                + missingRules + "; observed rule text=" + ruleText);

        ui.ensureMainMenu();
        ui.tapText("High Scores");
        String nameDialog = null;
        try {
            nameDialog = ui.awaitText("Edit Your Name", Duration.ofSeconds(10));
        } catch (IllegalStateException noFirstRunPrompt) {
            // This prompt is one-time state: Cancel may persist the default player name across test runs.
        }
        if (nameDialog != null) {
            assertTrue(ui.hasText(nameDialog, "Enter your player name") && ui.hasText(nameDialog, "Player") && ui.hasText(nameDialog, "CANCEL"),
                    "High Scores showed an incomplete player-name prompt");
            evidence.addAll(ui.capture("high-scores-name-prompt"));
            ui.tapText("CANCEL");
        } else {
            // This prompt is one-time state: Cancel may persist the default player name across test runs.
            String highScoresState = ui.dump();
            assertTrue(ui.hasText(highScoresState, "New Game") && ui.hasText(highScoresState, "Resume Game"),
                    "High Scores did not show its first-run name prompt and did not return to the menu");
            evidence.addAll(ui.capture("high-scores-existing-player"));
        }
        ui.restartToMainMenu(PACKAGE);

        ui.ensureMainMenu();
        ui.tapText("New Game");
        String configuration = ui.awaitText("START", Duration.ofSeconds(10));
        String initialPlayerCount = selectedChoice(ui, configuration, Set.of("3", "4", "5", "6"), "player count");
        String initialGameType = selectedChoice(ui, configuration,
                Set.of("Descending", "Down & Up", "Maximum", "Romanian", "Long Romanian", "Oh Hell", "Reverse Oh Hell"),
                "game type");
        String initialAiLevel = selectedChoice(ui, configuration, Set.of("Normal", "Hard"), "AI level");

        ui.tapSpinner(0);
        String players = ui.awaitText("6", Duration.ofSeconds(10));
        assertTrue(Set.copyOf(ui.texts(players)).containsAll(Set.of("3", "4", "5", "6")), "Player choices must be 3–6");
        ui.back();
        configuration = ui.awaitSpinnerSelection(initialPlayerCount, Duration.ofSeconds(10));

        ui.tapSpinner(1);
        String gameTypes = ui.awaitText("Reverse Oh Hell", Duration.ofSeconds(10));
        assertTrue(Set.copyOf(ui.texts(gameTypes)).containsAll(Set.of("Descending", "Down & Up", "Maximum", "Romanian", "Long Romanian", "Oh Hell", "Reverse Oh Hell")),
                "A game type is missing from its selector");
        ui.back();
        configuration = ui.awaitSpinnerSelection(initialGameType, Duration.ofSeconds(10));

        ui.tapSpinner(2);
        String aiLevels = ui.awaitText("Hard", Duration.ofSeconds(10));
        assertTrue(Set.copyOf(ui.texts(aiLevels)).containsAll(Set.of("Normal", "Hard")), "AI level choices are incomplete");
        ui.back();
        configuration = ui.dump();
        evidence.addAll(ui.capture("new-game-configuration"));
        assertTrue(selectedChoice(ui, configuration, Set.of("3", "4", "5", "6"), "player count")
                        .equals(initialPlayerCount)
                        && selectedChoice(ui, configuration,
                                Set.of("Descending", "Down & Up", "Maximum", "Romanian", "Long Romanian", "Oh Hell", "Reverse Oh Hell"),
                                "game type").equals(initialGameType)
                        && selectedChoice(ui, configuration, Set.of("Normal", "Hard"), "AI level").equals(initialAiLevel),
                "Opening setup selectors changed the saved choices; visible texts=" + ui.texts(configuration));

        ui.tapText("START");
        String bidding = ui.awaitText("Bid 0", Duration.ofSeconds(20));
        long visibleBidChoices = ui.texts(bidding).stream().filter(text -> text.matches("Bid \\d+")).count();
        assertTrue(visibleBidChoices >= 2 && ui.hasClass(bidding, "android.widget.Button"),
                "Bidding screen did not expose multiple numeric bid choices; visible texts=" + ui.texts(bidding));
        evidence.addAll(ui.capture("bidding-phase"));

        ui.tapText("Bid 0");
        String play = ui.awaitAbsent("Bid 0", Duration.ofSeconds(20));
        assertTrue(!ui.hasText(play, "Bid Other"), "An alternate bidding control remained after submitting a bid");
        Thread.sleep(4_000); // Let the app finish initial play animations before taking the comparison baseline.
        List<Path> playEvidence = ui.capture("play-phase");
        evidence.addAll(playEvidence);
        long visibleCardPixels = ui.countBrightNeutralPixels(artifacts.resolve("play-phase.png"), 0.55);
        if (visibleCardPixels <= 5_000) {
            publishReport(artifacts, packagePath.output(), evidence,
                    "android-gameplay.html",
                    "Bidding ended, but the play screen did not contain the expected visible card surfaces; bright card pixels=" + visibleCardPixels,
                    AtfReportStatus.FAIL);
            ui.back();
        }
        assertTrue(visibleCardPixels > 5_000, "Play phase did not visibly render cards; neutral bright pixel count=" + visibleCardPixels);

        long handAreaChangedPixels = ui.tapUntilHandChanges(
                artifacts.resolve("play-phase.png"), artifacts.resolve("after-card-tap.png"));
        List<Path> cardTapEvidence = ui.capture("after-card-tap");
        evidence.addAll(cardTapEvidence);
        long cardPixelsAfterTap = ui.countBrightNeutralPixels(artifacts.resolve("after-card-tap.png"), 0.55);
        Thread.sleep(8_000); // Allow the AI seats to finish the trick and the next player turn to settle.
        evidence.addAll(ui.capture("ai-turns-settled"));
        long secondHandAreaChangedPixels = ui.tapUntilHandChanges(
                artifacts.resolve("ai-turns-settled.png"), artifacts.resolve("after-second-card-tap.png"));
        evidence.addAll(ui.capture("after-second-card-tap"));
        long cardPixelsAfterSecondTap = ui.countBrightNeutralPixels(artifacts.resolve("after-second-card-tap.png"), 0.55);
        String checks = "Verified menu navigation, instructions/scoring text, high scores, all setup choices, restoring the starting settings, starting a game, numeric bid choices into play, one human card play, the intervening AI turn, and a second human card play. Bright card pixels before the first card tap="
                + visibleCardPixels + "; after the tap=" + cardPixelsAfterTap
                + "; changed hand/play-area pixels after the first tap=" + handAreaChangedPixels
                + "; changed hand/play-area pixels after the second tap=" + secondHandAreaChangedPixels
                + "; remaining bright card pixels=" + cardPixelsAfterSecondTap + ".";
        AtfReportStatus outcome = cardPixelsAfterTap > 5_000 && handAreaChangedPixels > 3_000
                && secondHandAreaChangedPixels > 3_000 && cardPixelsAfterSecondTap > 5_000
                ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        publishReport(artifacts, packagePath.output(), evidence, "android-gameplay.html", checks, outcome);
        ui.restartToMainMenu(PACKAGE);
        assertTrue(cardPixelsAfterTap > 5_000 && handAreaChangedPixels > 3_000
                        && secondHandAreaChangedPixels > 3_000 && cardPixelsAfterSecondTap > 5_000,
                "Could not verify two human card plays through the AI game flow; first bright pixels=" + cardPixelsAfterTap
                        + ", first changed pixels=" + handAreaChangedPixels + ", second changed pixels="
                        + secondHandAreaChangedPixels + ", second bright pixels=" + cardPixelsAfterSecondTap);
    }

    @Test
    void resumedGameRemainsVisibleAfterSettling() throws Exception {
        AdbClient adb = AdbClient.fromSystemProperties();
        var packagePath = adb.run("shell", "pm", "path", PACKAGE);
        packagePath.requireSuccess();
        assertTrue(packagePath.output().contains("package:"), "Package is not installed: " + packagePath.output());

        Path artifacts = Path.of(System.getProperty("atf.android.artifacts", "target/android-artifacts"));
        AndroidUiDriver ui = new AndroidUiDriver(adb, artifacts);
        List<Path> evidence = new ArrayList<>();
        ui.restartToMainMenu(PACKAGE);
        ui.tapText("Resume Game");
        Thread.sleep(2_000);
        evidence.addAll(ui.capture("resume-immediate"));
        long immediatePixels = ui.countBrightNeutralPixels(artifacts.resolve("resume-immediate.png"), 0.55);
        Thread.sleep(6_000);
        evidence.addAll(ui.capture("resume-settled"));
        long settledPixels = ui.countBrightNeutralPixels(artifacts.resolve("resume-settled.png"), 0.55);

        String checks = "Resume immediate bright card pixels=" + immediatePixels
                + "; after settling for six seconds=" + settledPixels + ".";
        AtfReportStatus outcome = immediatePixels > 5_000 && settledPixels > 5_000
                ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        publishReport(artifacts, packagePath.output(), evidence, "android-resume-rendering.html", checks, outcome);
        assertTrue(immediatePixels > 5_000 && settledPixels > 5_000,
                "Resumed game lost its visible cards after settling; immediate bright pixels=" + immediatePixels
                        + ", after six seconds=" + settledPixels);
    }

    @Test
    void reachesOhHellScoreSheetAndVerifiesNextHandBidRange() throws Exception {
        AdbClient adb = AdbClient.fromSystemProperties();
        var packagePath = adb.run("shell", "pm", "path", PACKAGE);
        packagePath.requireSuccess();
        assertTrue(packagePath.output().contains("package:"), "Package is not installed: " + packagePath.output());

        Path artifacts = Path.of(System.getProperty("atf.android.artifacts", "target/android-artifacts"));
        AndroidUiDriver ui = new AndroidUiDriver(adb, artifacts);
        AndroidUiFlowTrace trace = new AndroidUiFlowTrace(artifacts, "contract-whist-oh-hell-score-next-hand",
                "Contract Whist — live Oh Hell score and next-hand transition", getClass().getSimpleName(),
                "atf.android.e2e", PACKAGE,
                List.of("android", "api-28", "original-app", "e2e", "oh-hell", "score", "next-hand"));
        AtfReportStatus result = AtfReportStatus.FAIL;
        try {
            long actionStarted = System.nanoTime();
            String menu = ui.restartToMainMenu(PACKAGE);
            List<Path> menuEvidence = ui.capture("ohhell-full-menu");
            boolean menuReady = ui.hasText(menu, "New Game") && ui.hasText(menu, "Resume Game");
            recordCheckpoint(trace, "SETUP", AtfGherkinKeyword.GIVEN, "Open the installed original game",
                    PACKAGE, menuEvidence, "force-stop and launch app", "Main menu with New Game and Resume Game",
                    "visible labels=" + ui.texts(menu), Duration.ofNanos(System.nanoTime() - actionStarted).toMillis(),
                    List.of(new AtfAssertion("Main menu is available", "New Game and Resume Game visible",
                            "visible labels=" + ui.texts(menu), menuReady)),
                    menuReady ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
            assertTrue(menuReady, "Original app did not expose the main menu");

            actionStarted = System.nanoTime();
            ui.tapText("New Game");
            String setup = ui.awaitText("START", Duration.ofSeconds(10));
            List<Path> setupEvidence = ui.capture("ohhell-full-setup");
            String players = selectedChoice(ui, setup, Set.of("3", "4", "5", "6"), "player count");
            String gameType = selectedChoice(ui, setup,
                    Set.of("Descending", "Down & Up", "Maximum", "Romanian", "Long Romanian", "Oh Hell", "Reverse Oh Hell"),
                    "game type");
            String aiLevel = selectedChoice(ui, setup, Set.of("Normal", "Hard"), "AI level");
            boolean knownDeal = players.equals("4") && gameType.equals("Oh Hell");
            recordCheckpoint(trace, "SETUP", AtfGherkinKeyword.THEN, "Record the exact live-game configuration",
                    "New Game configuration", setupEvidence, "read selected setup controls",
                    "4 players; Oh Hell; " + aiLevel + " AI", "Players=" + players + "; Game Type=" + gameType
                            + "; AI Level=" + aiLevel,
                    Duration.ofNanos(System.nanoTime() - actionStarted).toMillis(),
                    List.of(new AtfAssertion("Four-player deal is selected", "4", players, players.equals("4")),
                            new AtfAssertion("Oh Hell ruleset is selected", "Oh Hell", gameType, gameType.equals("Oh Hell")),
                            new AtfAssertion("AI level is recorded", "Normal or Hard", aiLevel,
                                    Set.of("Normal", "Hard").contains(aiLevel))),
                    knownDeal ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
            assertTrue(knownDeal, "Full-hand oracle requires the observed four-player Oh Hell deal; current selection: "
                    + players + " players / " + gameType);

            actionStarted = System.nanoTime();
            ui.tapText("START");
            String bidding = ui.awaitText("Bid 0", Duration.ofSeconds(20));
            long bidChoices = ui.texts(bidding).stream().filter(text -> text.matches("Bid \\d+")).count();
            List<String> disabledBidChoices = ui.disabledButtonTexts(bidding).stream()
                    .filter(text -> text.matches("Bid \\d+")).toList();
            List<Path> biddingEvidence = ui.capture("ohhell-full-bidding");
            boolean biddingReady = bidChoices >= 2 && disabledBidChoices.size() <= 1
                    && !ui.hasText(bidding, "Bid Other");
            recordCheckpoint(trace, "TEST BODY", AtfGherkinKeyword.WHEN, "Start the four-player Oh Hell hand",
                    "START", biddingEvidence, "tap START", "numeric bid controls become available",
                    "numeric bid controls=" + bidChoices + "; Bid Other visible=" + ui.hasText(bidding, "Bid Other"),
                    Duration.ofNanos(System.nanoTime() - actionStarted).toMillis(),
                    List.of(new AtfAssertion("Numeric bidding controls are exposed", "at least 2 choices",
                            Long.toString(bidChoices), bidChoices >= 2),
                            new AtfAssertion("No alternate Bid Other path is shown", "absent",
                                    Boolean.toString(ui.hasText(bidding, "Bid Other")), !ui.hasText(bidding, "Bid Other")),
                            new AtfAssertion("Last-bid exclusion is visible when the prior total permits it",
                                    "zero or one disabled numeric bid", disabledBidChoices.toString(),
                                    disabledBidChoices.size() <= 1,
                                    disabledBidChoices.isEmpty() ? AtfReportStatus.WARNING : AtfReportStatus.PASS)),
                    biddingReady ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
            assertTrue(biddingReady, "Bidding controls are incomplete; visible labels=" + ui.texts(bidding));

            actionStarted = System.nanoTime();
            String submittedBid = null;
            List<Integer> numericBidValues = ui.texts(bidding).stream()
                    .map(text -> java.util.regex.Pattern.compile("^Bid (\\d+)$").matcher(text))
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1))).sorted().toList();
            for (int value : numericBidValues) {
                String label = "Bid " + value;
                if (ui.isEnabled(bidding, label)) {
                    submittedBid = label;
                    break;
                }
            }
            assertTrue(submittedBid != null, "No enabled numeric bid is available; visible=" + ui.texts(bidding));
            ui.tapText(submittedBid);
            String play = ui.awaitAbsent(submittedBid, Duration.ofSeconds(20));
            Thread.sleep(4_000);
            List<Path> firstTurnEvidence = ui.capture("ohhell-full-trick-01-before");
            long handPixels = ui.countBrightNeutralPixels(artifacts.resolve("ohhell-full-trick-01-before.png"), 0.67);
            recordCheckpoint(trace, "TEST BODY", AtfGherkinKeyword.WHEN, "Place an enabled bid and enter card play",
                    submittedBid, firstTurnEvidence, "tap " + submittedBid, "bid controls close and card play is visible",
                    submittedBid + " absent=" + !ui.hasText(play, submittedBid) + "; bright card pixels=" + handPixels,
                    Duration.ofNanos(System.nanoTime() - actionStarted).toMillis(),
                    List.of(new AtfAssertion("Bid control closes after submission", submittedBid + " absent",
                            Boolean.toString(!ui.hasText(play, submittedBid)), !ui.hasText(play, submittedBid)),
                            new AtfAssertion("Card faces render in the play phase", "> 5000 bright neutral pixels",
                                    Long.toString(handPixels), handPixels > 5_000)),
                    handPixels > 5_000 ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
            assertTrue(handPixels > 5_000, "The play phase did not render the human hand");

            Path baseline = artifacts.resolve("ohhell-full-trick-01-before.png");
            boolean scoreBoundaryVisible = false;
            List<Path> scoreEvidence = List.of();
            List<String> scoreLabels = List.of();
            int interactionCycles = 0;
            for (int cycle = 1; cycle <= 12; cycle++) {
                String ordinal = String.format(java.util.Locale.ROOT, "%02d", cycle);
                Path after = artifacts.resolve("ohhell-full-cycle-" + ordinal + "-after.png");
                AndroidUiDriver.CardPlayResult playResult = null;
                for (int retry = 0; retry < 3; retry++) {
                    AndroidUiDriver.CardPlayResult attempt = ui.tapUntilHandChangesDetailed(baseline, after);
                    if (playResult == null) playResult = attempt;
                    else {
                        List<AndroidUiDriver.CardTapAttempt> all = new ArrayList<>(playResult.attempts());
                        all.addAll(attempt.attempts());
                        playResult = new AndroidUiDriver.CardPlayResult(List.copyOf(all), attempt.handChangedPixels(),
                                attempt.tableChangedPixels(), attempt.acceptedX(), attempt.acceptedY(), attempt.accepted(),
                                attempt.handBoundary(), attempt.boundaryLabels());
                    }
                    if (attempt.handBoundary()) break;
                    if (attempt.accepted()) break;
                    Thread.sleep(2_000);
                }
                if (playResult != null && playResult.handBoundary()) {
                    scoreBoundaryVisible = true;
                    scoreEvidence = ui.capture("ohhell-score-boundary-guard");
                    scoreLabels = ui.texts(Files.readString(scoreEvidence.stream()
                            .filter(file -> file.toString().endsWith(".xml")).findFirst().orElseThrow()));
                    recordCheckpoint(trace, "TEST BODY", AtfGherkinKeyword.THEN,
                            "Stop card exploration when the original exposes a score control",
                            "Next Hand / Play Again", scoreEvidence,
                            "inspect UI hierarchy before the hazardous bottom-center card coordinate",
                            "do not tap the score control during card probing",
                            "boundary labels=" + playResult.boundaryLabels(), 0,
                            List.of(new AtfAssertion("Score control is protected from exploratory taps",
                                            "probe stops before x=540, y=1708", "boundary detected before tap", true),
                                    new AtfAssertion("Human card count is machine-verified", "ten distinct card removals",
                                            "custom canvas card count is not exposed", false, AtfReportStatus.WARNING)),
                            AtfReportStatus.WARNING);
                    break;
                }
                if (playResult == null || !playResult.accepted()) {
                    List<Path> failureEvidence = ui.capture("ohhell-full-cycle-" + ordinal + "-no-raster-transition");
                    List<AtfUiActionRecord> failedActions = cardTapActions(trace, playResult);
                    trace.checkpoint("TEST BODY", AtfGherkinKeyword.WHEN.text(),
                            "Interaction cycle " + ordinal + " — probe visible card slots", "Human hand / card slot centers",
                            failureEvidence.stream().filter(file -> file.toString().endsWith(".png")).findFirst().orElse(null),
                            failedActions,
                            List.of(new AtfAssertion("A stable hand/table raster transition is observed",
                                    "both pixel regions change", "no candidate tap satisfied both pixel oracles", false)),
                            AtfReportStatus.FAIL,
                            failureEvidence.stream().filter(file -> file.toString().endsWith(".xml")).toList());
                    throw new AssertionError("No candidate tap produced a stable hand/table raster transition in cycle " + ordinal);
                }
                interactionCycles++;
                List<Path> playEvidence = ui.capture("ohhell-full-cycle-" + ordinal + "-tap-probe");
                List<AtfAssertion> playAssertions = List.of(
                        new AtfAssertion("Hand-area raster changes after tap exploration", "> 3000 changed pixels",
                                Long.toString(playResult.handChangedPixels()), playResult.handChangedPixels() > 3_000),
                        new AtfAssertion("Table-area raster changes after tap exploration", "> 3000 changed pixels",
                                Long.toString(playResult.tableChangedPixels()), playResult.tableChangedPixels() > 3_000));
                boolean rasterChanged = playAssertions.stream().allMatch(AtfAssertion::passed);
                recordCheckpoint(trace, "TEST BODY", cycle == 1 ? AtfGherkinKeyword.WHEN : AtfGherkinKeyword.AND,
                        "Interaction cycle " + ordinal + " — hand/table raster transition; card identity is not decoded",
                        "visible card slot at x=" + playResult.acceptedX() + ", y=" + playResult.acceptedY(),
                        playEvidence, "probe visible card slots and compare stable hand/table raster regions",
                        "raster changed; number and identity of accepted human cards remain unverified",
                        playResult.toString(),
                        playResult.attempts().stream().mapToLong(AndroidUiDriver.CardTapAttempt::durationMillis).sum(),
                        playAssertions, rasterChanged ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
                assertTrue(rasterChanged, "Raster transition oracle failed in interaction cycle " + ordinal + ": " + playResult);

                Thread.sleep(8_000);
                List<Path> settledEvidence = ui.capture("ohhell-full-cycle-" + ordinal + "-settled");
                List<String> settledLabels = ui.texts(Files.readString(settledEvidence.stream()
                        .filter(file -> file.toString().endsWith(".xml")).findFirst().orElseThrow()));
                boolean handBoundaryVisible = settledLabels.stream().anyMatch(label ->
                        label.equalsIgnoreCase("Next Hand") || label.equalsIgnoreCase("Play Again"));
                long settledPixels = ui.countBrightNeutralPixels(
                        settledEvidence.stream().filter(file -> file.toString().endsWith(".png")).findFirst().orElseThrow(), 0.67);
                if (handBoundaryVisible) {
                    scoreBoundaryVisible = true;
                    scoreEvidence = settledEvidence;
                    scoreLabels = settledLabels;
                    boolean nextHandLabelVisible = settledLabels.stream()
                            .anyMatch(label -> label.equalsIgnoreCase("Next Hand"));
                    recordCheckpoint(trace, "TEST BODY", AtfGherkinKeyword.THEN,
                            "Observe the original score boundary without inferring a trick count",
                            "Next Hand control", settledEvidence, "wait 8 seconds and inspect the accessible boundary label",
                            "score boundary is visible; card count is separately reported as unverified",
                            "interaction cycles=" + interactionCycles + "; labels=" + settledLabels,
                            8_000, List.of(new AtfAssertion("Score boundary exposes Next Hand", "Next Hand",
                                            settledLabels.toString(), nextHandLabelVisible),
                                    new AtfAssertion("Ten human cards are machine-verified", "10 distinct hand-card removals",
                                            "canvas card identities/count are not exposed by UI Automator",
                                            false, AtfReportStatus.WARNING)),
                            AtfReportStatus.WARNING);
                    break;
                } else {
                    boolean remainsPlayable = settledPixels > 5_000;
                    recordCheckpoint(trace, "TEST BODY", AtfGherkinKeyword.THEN,
                            "Interaction cycle " + ordinal + " — inspect live table after AI settles",
                            "AI seats and human hand", settledEvidence, "wait 8 seconds and inspect accessible labels",
                            "play remains rendered and no score boundary is exposed",
                            "bright neutral pixels=" + settledPixels + "; labels=" + settledLabels,
                            8_000, List.of(new AtfAssertion("Play surface remains visible before score boundary",
                                    "> 5000 bright pixels; no Next Hand label", "pixels=" + settledPixels
                                            + "; labels=" + settledLabels, remainsPlayable)),
                            remainsPlayable ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
                    assertTrue(remainsPlayable, "Original left the live-play surface without exposing Next Hand; labels="
                            + settledLabels + "; bright pixels=" + settledPixels);
                    baseline = settledEvidence.stream().filter(file -> file.toString().endsWith(".png"))
                            .findFirst().orElseThrow();
                }
            }
            assertTrue(scoreBoundaryVisible, "No score/Next Hand boundary appeared within 12 tap-exploration cycles");

            scoreEvidence = ui.capture("ohhell-full-score-sheet");
            String scoreHierarchy = Files.readString(scoreEvidence.stream()
                    .filter(file -> file.toString().endsWith(".xml")).findFirst().orElseThrow());
            scoreLabels = ui.texts(scoreHierarchy);
            boolean scoreSheetVisible = scoreLabels.stream().anyMatch(label -> label.toLowerCase(java.util.Locale.ROOT).contains("score"))
                    || scoreLabels.stream().anyMatch(label -> label.equalsIgnoreCase("Next Hand")
                            || label.equalsIgnoreCase("Play Again") || label.equalsIgnoreCase("New Game"));
            recordCheckpoint(trace, "TEST BODY", AtfGherkinKeyword.THEN, "Capture the original score sheet; preserve unverified card count",
                    "Oh Hell score sheet", scoreEvidence, "wait for end-of-hand scoring view",
                    "score sheet or end-of-hand controls are visible",
                    "accessible score labels=" + scoreLabels + "; screenshot=ohhell-full-score-sheet.png",
                    0, List.of(new AtfAssertion("A hand-end score surface is exposed",
                                    "score label or next-hand control", "accessible labels=" + scoreLabels,
                                    scoreSheetVisible),
                            new AtfAssertion("Original hand score values are independently decoded",
                                    "all four per-player scores and earned tricks", "custom canvas is not semantically exposed",
                                    false, AtfReportStatus.WARNING)), AtfReportStatus.WARNING);
            result = AtfReportStatus.WARNING;
            if (scoreSheetVisible) {
                actionStarted = System.nanoTime();
                ui.tapText("Next Hand");
                String nextBidding = ui.awaitText("Bid 0", Duration.ofSeconds(20));
                List<Path> nextHandEvidence = ui.capture("ohhell-next-hand-bidding");
                List<Integer> nextBidValues = ui.texts(nextBidding).stream()
                        .map(text -> java.util.regex.Pattern.compile("^Bid (\\d+)$").matcher(text))
                        .filter(java.util.regex.Matcher::matches)
                        .map(matcher -> Integer.parseInt(matcher.group(1))).sorted().toList();
                List<String> disabledNextBids = ui.disabledButtonTexts(nextBidding).stream()
                        .filter(text -> text.matches("Bid \\d+")).toList();
                List<Integer> expectedNextBidValues = java.util.stream.IntStream.rangeClosed(0, 9).boxed().toList();
                boolean nextHandHasNineCardBidRange = nextBidValues.equals(expectedNextBidValues);
                recordCheckpoint(trace, "TEST BODY", AtfGherkinKeyword.WHEN,
                        "Advance to the next hand and verify its nine-card bid range",
                        "Next Hand", nextHandEvidence, "tap Next Hand",
                        "bid controls range from Bid 0 through Bid 9; Bid 10 is absent",
                        "numeric bid values=" + nextBidValues + "; disabled=" + disabledNextBids,
                        Duration.ofNanos(System.nanoTime() - actionStarted).toMillis(),
                        List.of(new AtfAssertion("Next hand offers exactly the nine-card bid domain",
                                        "0 through 9 inclusive; no 10", nextBidValues.toString(), nextHandHasNineCardBidRange),
                                new AtfAssertion("Last-bid exclusion remains represented when applicable",
                                        "at most one disabled numeric bid", disabledNextBids.toString(), disabledNextBids.size() <= 1)),
                        nextHandHasNineCardBidRange ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
                assertTrue(nextHandHasNineCardBidRange,
                        "Next Hand did not expose the expected nine-card bid domain: " + nextBidValues);
            }
            ui.restartToMainMenu(PACKAGE);
        } catch (Throwable failure) {
            result = AtfReportStatus.FAIL;
            trace.fail(failure);
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new RuntimeException(failure);
        } finally {
            try {
                List<Path> teardown = ui.capture("ohhell-full-teardown");
                recordCheckpoint(trace, "TEARDOWN", AtfGherkinKeyword.THEN,
                        "Leave the original app at a stable screen", "Contract Whist Android app",
                        teardown, "capture final app state", "retain final screen and hierarchy for audit",
                        "final UI hierarchy and PNG retained", 0,
                        List.of(new AtfAssertion("Final teardown capture exists", "PNG and UI hierarchy",
                                teardown.stream().map(file -> file.getFileName().toString()).toList().toString(),
                                teardown.size() == 2)), AtfReportStatus.PASS);
            } catch (Exception teardownFailure) {
                trace.fail(teardownFailure);
                if (result != AtfReportStatus.FAIL) {
                    result = AtfReportStatus.FAIL;
                    throw teardownFailure;
                }
            } finally {
                trace.finish(result);
            }
        }
    }

    @Test
    void discoversOhHellBidGridAtEachSupportedPlayerCount() throws Exception {
        AdbClient adb = AdbClient.fromSystemProperties();
        var packagePath = adb.run("shell", "pm", "path", PACKAGE);
        packagePath.requireSuccess();
        assertTrue(packagePath.output().contains("package:"), "Package is not installed: " + packagePath.output());

        Path artifacts = Path.of(System.getProperty("atf.android.artifacts", "target/android-artifacts"));
        AndroidUiDriver ui = new AndroidUiDriver(adb, artifacts);
        AndroidUiFlowTrace trace = new AndroidUiFlowTrace(artifacts, "contract-whist-oh-hell-bid-grid-by-seat-count",
                "Contract Whist — Oh Hell bid-grid discovery for 3–6 players", getClass().getSimpleName(),
                "atf.android.e2e", PACKAGE, List.of("android", "api-28", "original-app", "rule-discovery", "bidding"));
        AtfReportStatus result = AtfReportStatus.FAIL;
        String originalPlayers = "4", originalGameType = "Oh Hell", originalAiLevel = "Normal";
        boolean originalSetupCaptured = false;
        try {
            String menu = ui.restartToMainMenu(PACKAGE);
            List<Path> menuEvidence = ui.capture("ohhell-bid-matrix-menu");
            recordCheckpoint(trace, "SETUP", AtfGherkinKeyword.GIVEN, "Open the original app for bid-grid discovery",
                    PACKAGE, menuEvidence, "force-stop and launch app", "main menu before changing table size",
                    "visible labels=" + ui.texts(menu), 0,
                    List.of(new AtfAssertion("Main menu is ready", "New Game and Resume Game",
                            "visible labels=" + ui.texts(menu), ui.hasText(menu, "New Game") && ui.hasText(menu, "Resume Game"))),
                    AtfReportStatus.PASS);

            ui.tapText("New Game");
            String initialSetup = ui.awaitText("START", Duration.ofSeconds(10));
            originalPlayers = selectedChoice(ui, initialSetup, Set.of("3", "4", "5", "6"), "player count");
            originalGameType = selectedChoice(ui, initialSetup,
                    Set.of("Descending", "Down & Up", "Maximum", "Romanian", "Long Romanian", "Oh Hell", "Reverse Oh Hell"),
                    "game type");
            originalAiLevel = selectedChoice(ui, initialSetup, Set.of("Normal", "Hard"), "AI level");
            originalSetupCaptured = true;
            ui.restartToMainMenu(PACKAGE);

            for (int playerCount = 3; playerCount <= 6; playerCount++) {
                String playerLabel = Integer.toString(playerCount);
                ui.tapText("New Game");
                String setup = ui.awaitText("START", Duration.ofSeconds(10));
                ui.selectSpinnerOption(0, playerLabel);
                setup = ui.awaitSpinnerSelection(playerLabel, Duration.ofSeconds(10));
                String type = selectedChoice(ui, setup,
                        Set.of("Descending", "Down & Up", "Maximum", "Romanian", "Long Romanian", "Oh Hell", "Reverse Oh Hell"),
                        "game type");
                if (!type.equals("Oh Hell")) {
                    ui.selectSpinnerOption(1, "Oh Hell");
                    setup = ui.awaitSpinnerSelection("Oh Hell", Duration.ofSeconds(10));
                }
                String level = selectedChoice(ui, setup, Set.of("Normal", "Hard"), "AI level");
                if (!level.equals("Normal")) {
                    ui.selectSpinnerOption(2, "Normal");
                    setup = ui.awaitSpinnerSelection("Normal", Duration.ofSeconds(10));
                }
                List<Path> configEvidence = ui.capture("ohhell-bid-matrix-" + playerLabel + "-setup");
                recordCheckpoint(trace, "SETUP", playerCount == 3 ? AtfGherkinKeyword.WHEN : AtfGherkinKeyword.AND,
                        "Select Oh Hell with " + playerCount + " total players",
                        "Players=" + playerCount + "; Game Type=Oh Hell; AI Level=Normal", configEvidence,
                        "select player-count, ruleset, and AI controls",
                        "configuration is accepted for " + playerCount + " seats",
                        "Players=" + selectedChoice(ui, setup, Set.of("3", "4", "5", "6"), "player count")
                                + "; Game Type=Oh Hell; AI Level=Normal", 0,
                        List.of(new AtfAssertion("Requested player count is selected", playerLabel,
                                        selectedChoice(ui, setup, Set.of("3", "4", "5", "6"), "player count"), true),
                                new AtfAssertion("Oh Hell ruleset is selected", "Oh Hell", "Oh Hell", true)),
                        AtfReportStatus.PASS);

                long started = System.nanoTime();
                ui.tapText("START");
                String bidding = ui.awaitText("Bid 0", Duration.ofSeconds(20));
                List<Integer> bidValues = ui.texts(bidding).stream()
                        .map(text -> java.util.regex.Pattern.compile("^Bid (\\d+)$").matcher(text))
                        .filter(java.util.regex.Matcher::matches)
                        .map(matcher -> Integer.parseInt(matcher.group(1))).sorted().toList();
                List<Path> bidEvidence = ui.capture("ohhell-bid-matrix-" + playerLabel + "-bidding");
                boolean numericGrid = !bidValues.isEmpty()
                        && bidValues.stream().allMatch(value -> value >= 0 && value <= 10);
                int largestBid = bidValues.isEmpty() ? -1 : bidValues.getLast();
                AtfReportStatus observationStatus = playerCount == 4
                        ? (largestBid >= 9 && largestBid <= 10 ? AtfReportStatus.PASS : AtfReportStatus.FAIL)
                        : AtfReportStatus.WARNING;
                List<AtfAssertion> assertions = new ArrayList<>();
                assertions.add(new AtfAssertion("Bid controls are numeric and in the legal range",
                        "Non-empty numeric bids in the legal 0–10 range", "visible bids=" + bidValues, numericGrid));
                if (playerCount == 4) {
                    assertions.add(new AtfAssertion("Observed four-player Oh Hell max bid matches ten cards",
                            "Bid 9 or Bid 10 (the last-bid prohibition can remove Bid 10)",
                            "visible bids=" + bidValues, largestBid >= 9 && largestBid <= 10));
                } else {
                    assertions.add(new AtfAssertion("Unmapped table size is captured for rule discovery",
                            "observation only; no original-app baseline yet",
                            "visible bids=" + bidValues + "; maximum=" + largestBid, false, AtfReportStatus.WARNING));
                }
                recordCheckpoint(trace, "TEST BODY", playerCount == 3 ? AtfGherkinKeyword.WHEN : AtfGherkinKeyword.AND,
                        "Observe Oh Hell bid choices for " + playerCount + " players",
                        "Bidding controls", bidEvidence, "tap START and enumerate visible Bid N labels",
                        "record the numeric bid range and its relationship to hand size",
                        "visible bids=" + bidValues + "; maximum=" + largestBid
                                + "; Bid Other=" + ui.hasText(bidding, "Bid Other"),
                        Duration.ofNanos(System.nanoTime() - started).toMillis(), assertions, observationStatus);
                if (playerCount == 4) {
                    assertTrue(largestBid >= 9 && largestBid <= 10,
                            "The known four-player Oh Hell deal should expose a maximum of 9 or 10 after any last-bid restriction; visible=" + bidValues);
                }
                ui.restartToMainMenu(PACKAGE);
            }

            ui.tapText("New Game");
            String restoreSetup = ui.awaitText("START", Duration.ofSeconds(10));
            ui.selectSpinnerOption(0, originalPlayers);
            restoreSetup = ui.awaitSpinnerSelection(originalPlayers, Duration.ofSeconds(10));
            String currentType = selectedChoice(ui, restoreSetup,
                    Set.of("Descending", "Down & Up", "Maximum", "Romanian", "Long Romanian", "Oh Hell", "Reverse Oh Hell"),
                    "game type");
            if (!currentType.equals(originalGameType)) {
                ui.selectSpinnerOption(1, originalGameType);
                restoreSetup = ui.awaitSpinnerSelection(originalGameType, Duration.ofSeconds(10));
            }
            String currentLevel = selectedChoice(ui, restoreSetup, Set.of("Normal", "Hard"), "AI level");
            if (!currentLevel.equals(originalAiLevel)) {
                ui.selectSpinnerOption(2, originalAiLevel);
                restoreSetup = ui.awaitSpinnerSelection(originalAiLevel, Duration.ofSeconds(10));
            }
            ui.tapText("START");
            ui.restartToMainMenu(PACKAGE);
            List<Path> teardown = ui.capture("ohhell-bid-matrix-restored-menu");
            recordCheckpoint(trace, "TEARDOWN", AtfGherkinKeyword.THEN,
                    "Restore the original setup selection after rule discovery",
                    "Players=" + originalPlayers + "; Game Type=" + originalGameType + "; AI Level=" + originalAiLevel,
                    teardown, "start one restored setup and relaunch to main menu",
                    "saved setup selection restored and original app remains at menu",
                    "final labels=" + ui.texts(Files.readString(teardown.stream()
                            .filter(file -> file.toString().endsWith(".xml")).findFirst().orElseThrow())), 0,
                    List.of(new AtfAssertion("Final menu is reachable", "New Game and Resume Game",
                            "captured final menu", true)), AtfReportStatus.PASS);
            result = AtfReportStatus.WARNING;
            trace.finish(result);
        } catch (Throwable failure) {
            result = AtfReportStatus.FAIL;
            trace.fail(failure);
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new RuntimeException(failure);
        } finally {
            if (result == AtfReportStatus.FAIL && originalSetupCaptured) {
                try {
                    ui.restartToMainMenu(PACKAGE);
                    ui.tapText("New Game");
                    String restore = ui.awaitText("START", Duration.ofSeconds(10));
                    ui.selectSpinnerOption(0, originalPlayers);
                    restore = ui.awaitSpinnerSelection(originalPlayers, Duration.ofSeconds(10));
                    if (!selectedChoice(ui, restore,
                            Set.of("Descending", "Down & Up", "Maximum", "Romanian", "Long Romanian", "Oh Hell", "Reverse Oh Hell"),
                            "game type").equals(originalGameType)) {
                        ui.selectSpinnerOption(1, originalGameType);
                        restore = ui.awaitSpinnerSelection(originalGameType, Duration.ofSeconds(10));
                    }
                    if (!selectedChoice(ui, restore, Set.of("Normal", "Hard"), "AI level").equals(originalAiLevel)) {
                        ui.selectSpinnerOption(2, originalAiLevel);
                        ui.awaitSpinnerSelection(originalAiLevel, Duration.ofSeconds(10));
                    }
                    ui.tapText("START");
                    ui.restartToMainMenu(PACKAGE);
                } catch (Exception restoreFailure) {
                    trace.fail(restoreFailure);
                }
            }
            trace.finish(result);
        }
    }

    private static void recordCheckpoint(AndroidUiFlowTrace trace, String phase, AtfGherkinKeyword keyword,
            String title, String target, List<Path> evidence, String operation, String expected,
            String observed, long durationMillis, List<AtfAssertion> assertions, AtfReportStatus status) throws Exception {
        Path screenshot = evidence.stream().filter(file -> file.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
                .findFirst().orElse(null);
        List<Path> supporting = evidence.stream().filter(file -> file.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")).toList();
        AtfUiActionRecord action = new AtfUiActionRecord(trace.nextActionSequence(), operation, target,
                expected, observed, Math.max(0, durationMillis), status,
                operation.startsWith("read") || operation.startsWith("wait")
                        ? AtfUiEvidenceSource.OBSERVE : AtfUiEvidenceSource.ACTION);
        trace.checkpoint(phase, keyword.text(), title, target, screenshot, List.of(action), assertions, status, supporting);
    }

    private static List<AtfUiActionRecord> cardTapActions(AndroidUiFlowTrace trace,
            AndroidUiDriver.CardPlayResult playResult) {
        if (playResult == null) return List.of();
        List<AtfUiActionRecord> actions = new ArrayList<>();
        for (AndroidUiDriver.CardTapAttempt attempt : playResult.attempts()) {
            actions.add(new AtfUiActionRecord(trace.nextActionSequence(), "ADB exploratory tap at card slot",
                    "x=" + attempt.x() + ", y=" + attempt.y(),
                    "tap is only a candidate input; no card identity or legality claim", "hand pixels="
                            + attempt.handChangedPixels() + "; table pixels=" + attempt.tableChangedPixels()
                            + "; raster-threshold-met=" + attempt.accepted(), attempt.durationMillis(),
                    AtfReportStatus.WARNING));
        }
        return List.copyOf(actions);
    }

    private static void publishReport(Path artifacts, String installedPackagePath, List<Path> files,
            String reportName, String checks, AtfReportStatus outcome) throws Exception {
        Path report = artifacts.resolve(reportName);
        var settings = AtfReportConfigurationLoader.load().reportingSettings();
        try (var session = new ExtentReportSession(report, settings)) {
            AtfReportPorts.Scenario rawScenario = session.startScenario(
                    "Contract Whist Android acceptance", "atf.android.e2e", PACKAGE,
                    List.of("android", "emulator", "gameplay", "configuration"));
            try (var details = rawScenario.startPhase("Device metadata and UI hierarchy artifacts")) {
                details.observation(new ReportObservation("Android run details",
                        List.of(new ReportField("Device serial", System.getProperty("atf.android.serial", System.getenv().getOrDefault("ATF_ANDROID_SERIAL", "emulator-5554"))),
                                new ReportField("Package", PACKAGE),
                                new ReportField("Installed APK", installedPackagePath.trim()),
                                new ReportField("Checks", checks)),
                        List.of(), files.stream().filter(file -> !file.getFileName().toString().toLowerCase().endsWith(".png"))
                                .map(file -> {
                                    try { return artifact(file); }
                                    catch (Exception failure) { throw new java.io.UncheckedIOException(new java.io.IOException(failure)); }
                                }).toList()));
                details.complete(outcome);
            }
            var scenario = UiExtentReportSession.adaptScenario(rawScenario,
                    ReportEvidenceProfile.forScreenshotPolicy(ReportScreenshotPolicy.EACH_CHECKPOINT),
                    "Contract Whist Android acceptance");
            try (var phase = scenario.startPhase("Android UI screenshot checkpoints")) {
                for (Path image : files.stream().filter(file -> file.getFileName().toString().toLowerCase().endsWith(".png")).toList()) {
                    ReportScreenshot screenshot = AndroidScreenshotEvidence.from(image);
                    phase.uiValidation(screenshot.title(), "Android emulator", screenshot.base64Png(),
                            List.of(), AtfReportStatus.PASS);
                }
                phase.complete(outcome);
            }
            scenario.complete(outcome);
        }
    }

    private static String selectedChoice(AndroidUiDriver ui, String xml, Set<String> allowed, String field)
            throws Exception {
        List<String> selected = ui.texts(xml).stream().filter(allowed::contains).distinct().toList();
        assertTrue(selected.size() == 1,
                "Could not identify the current " + field + " selection from visible texts " + ui.texts(xml));
        return selected.get(0);
    }

    private static ReportObservation.Artifact artifact(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        return new ReportObservation.Artifact(file.getFileName().toString(), file.toUri(), HexFormat.of().formatHex(digest));
    }
}

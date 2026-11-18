package ro.rentzlab.browser;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import ro.cardtable.browser.BrowserExperience;
import ro.cardtable.browser.BrowserGame;
import ro.cardtable.ui.turn.TurnPacing;
import ro.rentzlab.app.achievements.AchievementCatalog;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.ScoreProfile;

/** Offline component checks for the Rentz adapter boundary; no browser or emulator is required. */
public final class RentzBrowserComponentTest {
    private static int checks;

    private RentzBrowserComponentTest() { }

    public static void main(String[] args) {
        sharedPresentationComesFromTheLibrary();
        everyTrickCardIsVisible();
        setupSupportsEveryTableSize();
        checks += ro.cardtable.browser.BrowserSessionRentzContractTest.run();
        browserPaletteDefinesTheSharedShellTheme();
        browserRulesStayAlignedWithRentzScoring();
        contractChoicesFollowTheSelectedLanguage();
        browserNameMatchesNativeProfileContract();
        checkpointsRestoreTheSamePublicDecision();
        legalCardsAndSequenceAgree();
        browserHandOrderMatchesAndroid();
        automaticTurnsKeepTheirOrderAndTiming();
        rentzPlaybackKeepsThePlayedCardAndSequenceInLockstep();
        completedHandHistorySeparatesDeltaAndTotal();
        scoreHistoryUsesWhistShortLabelsAndLocalizedLegend();
        browserScoreProjectionMatchesTheSettledEngineForEveryContract();
        browserRentzAwardsOnlyOneZeroForEveryTableSize();
        browserFinalStandingsUseAndroidCompetitionTies();
        profileAndAchievementProgressSurviveAdapterRecreation();
        System.out.println("Rentz browser component checks passed: " + checks);
    }

    private static void sharedPresentationComesFromTheLibrary() {
        var browser = new RentzBrowser(() -> 31L);
        for (int players : List.of(3, 4, 5, 6)) for (int width : List.of(280, 360, 414, 600)) {
            for (int height : List.of(569, 896)) {
                var policy = new ro.cardtable.ui.table.TableViewportPolicy(height);
                var profile = browser.boardProfile("RENTZ", players, width, 0, policy);
                truth("Every table supplies the shared card layout " + players + "/" + width + "/" + height,
                        profile.playedCards() != null);
                equal("Exact shared card metrics independent of the contract",
                        ro.cardtable.ui.table.PlayedCardMetrics.forTable(players, width, 1f, policy), profile.playedCards());
                equal("Shared hand clearance", ro.cardtable.ui.hand.HandLayoutMetrics.LIFT_CLEARANCE_DP, profile.handClearance());
            }
        }
        var palette = browser.palette().stream().collect(Collectors.toMap(BrowserGame.Token::name, BrowserGame.Token::color));
        equal("Shared rank proportions", Float.toString(ro.cardtable.ui.cards.CardFaceMetrics.RANK_SCALE),palette.get("--whist-rank-scale"));
        equal("Shared played face outline", "#"+Integer.toHexString(ro.cardtable.ui.table.PlayedCardStyle.OUTLINE).substring(2),palette.get("--whist-played-line"));
        equal("Shared score human row", "#"+Integer.toHexString(ro.cardtable.ui.table.ScoreGridStyle.HUMAN_ROW).substring(2),palette.get("--whist-score-human"));
        var weights = ro.cardtable.ui.table.ScoreGridStyle.COLUMN_WEIGHTS;
        double total = (double) weights.get(0) + weights.get(3) + weights.get(4);
        for (int index = 0; index < 3; index++) {
            float weight = weights.get(index == 0 ? 0 : index + 2);
            equal("Native Jucator/P/T column proportion " + index, weight * 100 / total + "%", palette.get("--rentz-score-col-" + index));
        }
    }

    private static void browserPaletteDefinesTheSharedShellTheme() {
        var tokens = new RentzBrowser(() -> 1L).palette().stream()
                .collect(java.util.stream.Collectors.toMap(BrowserGame.Token::name, BrowserGame.Token::color));
        var required = Set.of("--whist-paper", "--whist-surface", "--whist-pine", "--whist-accent", "--whist-felt",
                "--whist-seat-fill", "--whist-seat-line", "--whist-seat-text", "--whist-turn-fill", "--whist-turn-line",
                "--whist-turn-text", "--whist-score-paper", "--whist-score-stripe", "--whist-score-human",
                "--whist-score-header", "--whist-score-rule", "--whist-score-ink", "--whist-score-muted",
                "--whist-hint-rejected-fill", "--whist-hint-rejected-line", "--whist-hint-legal-fill",
                "--whist-hint-legal-line", "--whist-played-fill", "--whist-played-line", "--whist-played-red",
                "--whist-played-dark", "--whist-played-player", "--whist-rank-scale", "--whist-hand-rank-cap",
                "--whist-hand-label", "--whist-face-index-top", "--whist-face-index-side",
                "--whist-face-suit-baseline", "--whist-face-suit-size", "--whist-face-inverted-size", "--whist-played-radius",
                "--whist-played-stroke", "--whist-hint-stroke", "--whist-hand-face-width", "--whist-hand-face-height",
                "--whist-trump-face-width", "--whist-trump-face-height", "--whist-hand-slot", "--whist-score-head",
                "--whist-score-row");
        truth("Rentz defines all CSS custom properties required by the J4 browser shell", tokens.keySet().containsAll(required));
        equal("Browser paper matches the Android background", "#f6f5ed", tokens.get("--whist-paper"));
        equal("Browser card surface matches native Rentz cards", "#fffef8", tokens.get("--whist-surface"));
        equal("Active seat matches native Rentz highlight", "#f1e6c8", tokens.get("--whist-turn-fill"));
        equal("Legal-card fill matches Android CardHighlight.IN_PLACE", "#fff6d7", tokens.get("--whist-hint-legal-fill"));
        equal("Legal-card outline matches Android CardHighlight.IN_PLACE", "#c99537", tokens.get("--whist-hint-legal-line"));
        equal("Legal-card stroke matches Android CardHighlight.IN_PLACE", "1.5px", tokens.get("--whist-hint-stroke"));
        equal("Shared hand geometry matches the Android card face", "88px", tokens.get("--whist-hand-face-height"));
    }

    private static void browserRulesStayAlignedWithRentzScoring() {
        var copy = new RentzBrowser(() -> 1L).interfaceCopy("ro");
        equal("Finishing-award table comes from the Rentz score profile",
                "3 jucători: +300 / +200 / 0; 4 jucători: +300 / +200 / +100 / 0; "
                        + "5 jucători: +300 / +175 / +100 / +25 / 0; "
                        + "6 jucători: +300 / +150 / +75 / +50 / +25 / 0",
                ruleBody(copy, "Premii de sosire Rentz"));
        equal("Contract scoring lists the engine's exact values",
                "Levată −10 · damă −25 · caro −20 · K♥ −100. Whist +10 · 10♣ +100. "
                        + "La Totale se adună primele patru categorii. Alegerea pe nevăzute dublează punctele mâinii.",
                ruleBody(copy, "Punctajul contractelor"));
        var en = new RentzBrowser(() -> 1L).interfaceCopy("en");
        truth("English rules expose the 6-player finishing row",
                ruleBody(en, "Rentz finishing awards").contains("6 players: +300 / +150 / +75 / +50 / +25 / 0"));
    }

    private static String ruleBody(Map<String, Object> copy, String title) {
        Object source = copy.get("rules");
        if (!(source instanceof List<?> rules)) throw new AssertionError("Rules list is missing");
        for (Object value : rules) {
            if (!(value instanceof Map<?, ?> rule)) continue;
            if (title.equals(rule.get("title"))) return String.valueOf(rule.get("body"));
        }
        throw new AssertionError("Missing rule: " + title);
    }

    private static void contractChoicesFollowTheSelectedLanguage() {
        var english = start(new RentzBrowser(() -> 20261117L), 4);
        english.setLanguage("en");
        english.act("reveal");
        var englishChoices = english.view().actions().stream()
                .filter(action -> action.key().startsWith("choose:"))
                .map(BrowserGame.Action::label).toList();
        equal("English browser choices match the Android picker labels",
                List.of("Tricks", "Queens", "Diamonds", "King of hearts", "Totals", "Whist", "Ten of clubs", "Rentz"),
                englishChoices);

        var romanian = start(new RentzBrowser(() -> 20261118L), 4);
        romanian.setLanguage("ro");
        romanian.act("reveal");
        var romanianChoices = romanian.view().actions().stream()
                .filter(action -> action.key().startsWith("choose:"))
                .map(BrowserGame.Action::label).toList();
        equal("Romanian browser choices preserve the APK labels",
                List.of("Levate", "Dame", "Carouri", "Regele de cupă", "Totale", "Whist", "Zece de treflă", "Rentz"),
                romanianChoices);

        var selected = start(new RentzBrowser(() -> 20261119L), 4);
        selected.setLanguage("en");
        selected.act("reveal");
        selected.act("choose:KING_HEARTS");
        equal("Selected contract status uses the same translated label", "King of hearts", selected.view().subtitle());
        equal("Browser game header localizes the selected contract", "King of hearts", selected.view().header().trump());
        equal("Browser progress line matches the Android title", "Hand 1/32", selected.view().header().progress());
        equal("Browser selected-game line matches Android without an Atu label", "King of hearts", selected.view().header().bids());

        var selectedRomanian = start(new RentzBrowser(() -> 20261120L), 4);
        selectedRomanian.setLanguage("ro");
        selectedRomanian.act("reveal");
        selectedRomanian.act("choose:KING_HEARTS");
        equal("Romanian browser game header matches the APK contract label", "Regele de cupă",
                selectedRomanian.view().header().trump());
        equal("Romanian progress line matches the Android hand counter", "Mâna 1/32",
                selectedRomanian.view().header().progress());
        equal("Romanian selected-game line shows only the chosen contract", "Regele de cupă",
                selectedRomanian.view().header().bids());
    }
    private static void browserNameMatchesNativeProfileContract() {
        var table = start(new RentzBrowser(() -> 1L), 3);
        table.validateName("Abc1234567");
        rejects("Browser enforces native 10-character name limit", () -> table.validateName("Abc12345678"));
        rejects("Browser rejects characters native profile rejects", () -> table.validateName("Ana<>"));
    }

    private static void checkpointsRestoreTheSamePublicDecision() {
        var browser = new RentzBrowser(() -> 776L);
        var table = start(browser, 4);
        table.act("reveal");
        table.act("choose:RENTZ");
        table.act(firstLegalAction(table));
        String checkpoint = browser.checkpoint(table);
        var restored = (RentzTable) browser.restore(checkpoint, "Ana");
        equal("Checkpoint returns to the same phase", table.observation().phase(), restored.observation().phase());
        equal("Checkpoint preserves the current hand", table.observation().hand(), restored.observation().hand());
        equal("Checkpoint preserves legal actions", table.observation().legal(), restored.observation().legal());
        equal("Checkpoint preserves public board", table.observation().board(), restored.observation().board());
        equal("Checkpoint preserves completed score history", table.completedHands(), restored.completedHands());
        equal("Checkpoint encoding is stable after restore", checkpoint, browser.checkpoint(restored));
        rejects("Damaged checkpoint is rejected", () -> browser.restore(checkpoint + "\nnot-a-command", "Ana"));
    }

    private static void setupSupportsEveryTableSize() {
        var browser = new RentzBrowser(() -> 20261116L);
        equal("Rentz title", "Rentz Lab", browser.title());
        equal("Shared native table surface", "whist", browser.surface());
        equal("Android first-frame pacing adapter", (int) TurnPacing.ACTION_DELAY_MILLIS, browser.autoDelayMillis());
        var playerField = browser.fields("ro").stream().filter(field -> field.key().equals("players")).findFirst().orElseThrow();
        equal("Player choices", List.of("3", "4", "5", "6"), playerField.options().stream()
                .map(BrowserGame.Option::value).toList());
        for (int players = 3; players <= 6; players++) {
            var table = start(browser, players);
            equal("Seat count " + players, players, table.view().seats().size());
            equal("Hidden hand before reveal " + players, 0, table.view().hand().size());
            equal("Active chooser " + players, 1L,
                    table.view().seats().stream().filter(BrowserGame.Seat::active).count());
            table.act("reveal");
            equal("Only the human hand is exposed after reveal " + players, 8, table.view().hand().size());
        }
    }

    private static void legalCardsAndSequenceAgree() {
        var table = start(new RentzBrowser(() -> 451L), 4);
        table.act("reveal");
        table.act("choose:RENTZ");
        table.act(firstLegalAction(table));
        var view = table.view();
        equal("Rentz sequence has four suit rows", 4, RentzTable.sequence(table.observation()).size());
        var legalFromHand = view.hand().stream().filter(card -> !card.action().isBlank())
                .map(BrowserGame.Card::action).collect(Collectors.toSet());
        var legalFromSequence = RentzTable.sequence(table.observation()).stream().flatMap(List::stream)
                .filter(RentzTable.MapCell::legal).map(RentzTable.MapCell::action).collect(Collectors.toSet());
        equal("Highlighted sequence cards are exactly legal moves", legalFromHand, legalFromSequence);
        equal("Only the active player is marked", 1L, view.seats().stream().filter(BrowserGame.Seat::active).count());
        truth("Only the current human hand is projected", view.hand().size() >= 1 && view.hand().size() <= 8);
        truth("Rentz map marks its opening rank", RentzTable.sequence(table.observation()).stream()
                .flatMap(List::stream).anyMatch(RentzTable.MapCell::opening));
        truth("Rentz map distinguishes played slots", RentzTable.sequence(table.observation()).stream()
                .flatMap(List::stream).anyMatch(RentzTable.MapCell::played));
    }

    private static void browserHandOrderMatchesAndroid() {
        var suitOrder = List.of(ro.rentzlab.core.Card.Suit.SPADES, ro.rentzlab.core.Card.Suit.HEARTS,
                ro.rentzlab.core.Card.Suit.CLUBS, ro.rentzlab.core.Card.Suit.DIAMONDS);
        Comparator<ro.rentzlab.core.Card> nativeOrder = Comparator
                .comparingInt((ro.rentzlab.core.Card card) -> suitOrder.indexOf(card.suit()))
                .thenComparingInt(ro.rentzlab.core.Card::rank);
        for (int players = 3; players <= 6; players++) {
            int count = players;
            var table = start(new RentzBrowser(() -> 20261130L + count), players);
            table.act("reveal");
            table.act("choose:RENTZ");
            for (int step = 0; step < 24 && table.observation().phase() == ro.rentzlab.core.RentzMatch.Phase.PLAY; step++) {
                var state = table.observation();
                var view = table.view();
                var expectedOrder = state.hand().stream().sorted(nativeOrder)
                        .map(ro.rentzlab.core.Card::label).toList();
                var actualOrder = view.hand().stream().map(BrowserGame.Card::label).toList();
                equal("Web hand follows Android suit/rank order " + players + "/" + step, expectedOrder, actualOrder);

                var expectedLegal = state.legal().stream()
                        .map(card -> "card:" + card.suit().name() + ":" + card.rank()).collect(Collectors.toSet());
                var actualLegal = view.hand().stream().map(BrowserGame.Card::action).filter(action -> !action.isBlank())
                        .collect(Collectors.toSet());
                equal("Web playable hand actions remain exact after sort " + players + "/" + step,
                        expectedLegal, actualLegal);

                if (expectedLegal.isEmpty()) {
                    var automatic = view.actions().stream().map(BrowserGame.Action::key)
                            .filter("auto-step"::equals).findFirst().orElseThrow();
                    table.act(automatic);
                } else {
                    table.act(expectedLegal.stream().sorted().findFirst().orElseThrow());
                }
            }
        }
    }

    private static void automaticTurnsKeepTheirOrderAndTiming() {
        var delay = new RentzBrowser(() -> 779L).autoDelayMillis();
        equal("Web decision delay matches the pinned Android first-frame cadence",
                TurnPacing.ACTION_DELAY_MILLIS, (long) delay);
        var table = start(new RentzBrowser(() -> 779L), 5);
        table.act("reveal");
        table.act("choose:RENTZ");
        table.act(firstLegalAction(table));
        var playback = RentzBrowser.sequencePlayback(table);
        truth("Rentz browser replay starts with the selected human card", !playback.isEmpty()
                && Integer.valueOf(0).equals(playback.get(0).get("actorSeat")));
        truth("Automated cards use the qualified Android first-frame interval", playback.stream().skip(1)
                .allMatch(step -> step.get("delayMillis") instanceof Number delayValue
                        && delayValue.longValue() == TurnPacing.ACTION_DELAY_MILLIS));
        truth("Automated playback follows the human card in seat order", playback.stream().skip(1)
                .allMatch(step -> !Integer.valueOf(0).equals(step.get("actorSeat"))));
        truth("Every playback step carries a public Rentz sequence snapshot", playback.stream()
                .allMatch(step -> step.get("before") instanceof Map<?, ?> && step.get("after") instanceof Map<?, ?>));
        equal("Rentz does not use trick collection animation", List.of(), table.collections());

        var before = table.acceptedCommandCount();
        table.act("auto-step");
        truth("One auto step advances at least one accepted command", table.acceptedCommandCount() > before);
        truth("Auto step leaves a legal player action or a completed hand",
                !table.view().actions().isEmpty() || !table.isFinished());
    }

    private static void rentzPlaybackKeepsThePlayedCardAndSequenceInLockstep() {
        for (int players = 3; players <= 6; players++) {
            int count = players;
            var table = start(new RentzBrowser(() -> 20261200L + count), count);
            table.act("reveal");
            table.act("choose:RENTZ");
            var before = table.observation();
            int initialPlayed = countPlayedCells(RentzBrowser.sequence(table));
            String action = firstLegalAction(table);
            String selected = before.hand().stream().filter(card -> action.equals("card:" + card.suit().name() + ":" + card.rank()))
                    .map(ro.rentzlab.core.Card::label).findFirst().orElseThrow();

            table.act(action);

            var frames = RentzBrowser.sequencePlayback(table);
            truth("The accepted human card is the first incremental Rentz frame at " + players + " seats", !frames.isEmpty());
            var first = frames.get(0);
            equal("The first Rentz frame belongs to the human at " + players + " seats", 0, first.get("actorSeat"));
            equal("The selected human card reuses the native lift instead of an AI delay", 0, first.get("delayMillis"));
            var beforeFrame = frameState(first, "before");
            var afterFrame = frameState(first, "after");
            equal("Pre-play sequence is unchanged at " + players + " seats",
                    initialPlayed, playedCards(beforeFrame));
            equal("The human card becomes public on the board in the same frame at " + players + " seats",
                    playedCards(beforeFrame) + 1, playedCards(afterFrame));
            truth("The played human card leaves the private hand before playback continues at " + players + " seats",
                    afterFrame.hand().stream().noneMatch(card -> card.label().equals(selected)));
            truth("The played human card is present in the public sequence at " + players + " seats",
                    afterFrame.sequence().stream().flatMap(row -> rowCells(row).stream())
                            .anyMatch(cell -> selected.equals(cell.get("label")) && Boolean.TRUE.equals(cell.get("played"))));

            int previous = playedCards(afterFrame);
            for (int index = 1; index < frames.size(); index++) {
                var frame = frames.get(index);
                equal("Opponent playback waits on the shared J4 first-frame cadence at " + players + "/" + index,
                        (int) TurnPacing.ACTION_DELAY_MILLIS, frame.get("delayMillis"));
                var snapshot = frameState(frame, "after");
                equal("Each opponent card advances exactly one Rentz sequence slot at " + players + "/" + index,
                        ++previous, playedCards(snapshot));
                truth("Playback snapshots expose only the human private hand at " + players + "/" + index,
                        snapshot.hand().size() <= 8);
            }
        }
    }

    private record PlaybackSnapshot(List<BrowserGame.Card> hand, List<Map<String, Object>> sequence) { }

    @SuppressWarnings("unchecked")
    private static PlaybackSnapshot frameState(Map<String, Object> frame, String key) {
        Object snapshot = frame.get(key);
        if (!(snapshot instanceof Map<?, ?> values) || !(values.get("hand") instanceof List<?> hand)
                || !(values.get("rentzSequence") instanceof List<?> sequence)) {
            throw new AssertionError("Rentz playback is missing its public " + key + " view snapshot");
        }
        return new PlaybackSnapshot((List<BrowserGame.Card>) hand, (List<Map<String, Object>>) sequence);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowCells(Map<String, Object> row) {
        return (List<Map<String, Object>>) row.get("cells");
    }

    private static int playedCards(PlaybackSnapshot snapshot) {
        return countPlayedCells(snapshot.sequence());
    }

    private static int countPlayedCells(List<Map<String, Object>> sequence) {
        return (int) sequence.stream().flatMap(row -> rowCells(row).stream())
                .filter(cell -> Boolean.TRUE.equals(cell.get("played"))).count();
    }

    private static void completedHandHistorySeparatesDeltaAndTotal() {
        var table = start(new RentzBrowser(() -> 981L), 3);
        table.act("reveal");
        table.act("choose:RENTZ");
        finishOneHand(table);
        var view = table.view();
        var grid = view.history().grid();
        equal("Hand number heading", "Mână", grid.cardsLabel());
        equal("Per-hand points use the Whist short heading", "P", grid.bidLabel());
        equal("Running total uses the Whist short heading", "T", grid.pointsLabel());
        equal("History explains short headings below the table", "P: punctele mâinii · T: total după mână", grid.legend());
        equal("One complete Rentz history row", 1, grid.rows().size());
        var row = grid.rows().get(0);
        equal("Hand row identifies the selected contract", "Rentz", row.status());
        equal("History keeps one score pair per seat", 3, row.cells().size());
        for (int seat = 0; seat < row.cells().size(); seat++) {
            var cell = row.cells().get(seat);
            equal("Delta comes from the confirmed hand", Integer.toString(table.completedHands().get(0).points().get(seat)), cell.bid());
            equal("Cumulative score comes from the confirmed total", Integer.toString(table.completedHands().get(0).totals().get(seat)), cell.total());
        }
    }

    private static void scoreHistoryUsesWhistShortLabelsAndLocalizedLegend() {
        var table = start(new RentzBrowser(() -> 982L), 4);
        table.setLanguage("en");
        table.act("reveal");
        table.act("choose:RENTZ");
        finishOneHand(table);
        var grid = table.view().history().grid();
        equal("English history keeps compact per-hand label", "P", grid.bidLabel());
        equal("English history keeps compact total label", "T", grid.pointsLabel());
        equal("English legend concisely distinguishes the values", "P: hand points · T: running total", grid.legend());
        var score = table.view().scoreDetails();
        truth("Completed-hand score uses the same P/T notation", score.awards().contains(" · P ")
                && score.awards().contains(" · T "));
        truth("Completed-hand history preserves every settled value",
                grid.rows().get(0).cells().stream().allMatch(cell -> !cell.bid().isBlank() && !cell.total().isBlank()));
    }

    private static void browserRentzAwardsOnlyOneZeroForEveryTableSize() {
        for (int players = 3; players <= 6; players++) {
            int count = players;
            var table = start(new RentzBrowser(() -> 20261117L + count), players);
            table.act("reveal");
            table.act("choose:RENTZ");
            finishOneHand(table);

            var hand = table.completedHands().get(0);
            var expectedByPlace = ScoreProfile.laboratory().finishRewardsFor(players);
            var actualByPlace = hand.scoring().finishOrder().stream().map(hand.points()::get).toList();
            equal("Browser Rentz awards match the Android laboratory profile for " + players,
                    expectedByPlace, actualByPlace);
            equal("Browser Rentz leaves exactly one zero-point player for " + players, 1L,
                    hand.points().stream().filter(points -> points == 0).count());

            var expectedAwards = java.util.stream.IntStream.range(0, hand.scoring().finishOrder().size())
                    .mapToObj(place -> {
                        int seat = hand.scoring().finishOrder().get(place);
                        return (place + 1) + ". " + table.names().get(seat) + " · P " + hand.points().get(seat)
                                + " · T " + hand.totals().get(seat) + "\n  Premiu de sosire · " + hand.points().get(seat);
                    }).collect(Collectors.joining("\n"));
            equal("Browser score details show native Rentz finish order for " + players,
                    expectedAwards, table.view().scoreDetails().awards());
            equal("Browser score details show exactly one zero award for " + players, 1L,
                    java.util.Arrays.stream(table.view().scoreDetails().awards().split("\\n"))
                            .filter(line -> !line.startsWith("  ") && line.contains(" · P 0 · T ")).count());

            var row = table.view().history().grid().rows().get(0);
            equal("Score history exposes one score cell per seat for " + players, players, row.cells().size());
            equal("Projected history keeps the engine's unique zero for " + players, 1L,
                    row.cells().stream().filter(cell -> "0".equals(cell.bid())).count());
            for (int seat = 0; seat < players; seat++) {
                equal("Projected score cell remains tied to the settled seat " + players + "/" + seat,
                        Integer.toString(hand.points().get(seat)), row.cells().get(seat).bid());
            }
        }
    }

    private static void browserScoreProjectionMatchesTheSettledEngineForEveryContract() {
        for (int players = 3; players <= 6; players++) {
            for (Contract contract : Contract.values()) {
                int seats = players;
                var table = start(new RentzBrowser(() -> 20261117L + seats * 100L + contract.ordinal()), players);
                table.act("reveal");
                table.act("choose:" + contract.name());
                finishOneHand(table);
                var hand = table.completedHands().get(0);
                var displayLines = table.view().scoreDetails().awards().lines()
                        .filter(line -> !line.startsWith("  ")).toList();
                var expected = (contract == Contract.RENTZ ? hand.scoring().finishOrder()
                        : java.util.stream.IntStream.range(0, players).boxed().toList()).stream()
                        .map(seat -> (contract == Contract.RENTZ ? (hand.scoring().finishOrder().indexOf(seat) + 1) + ". " : "")
                                + table.names().get(seat) + " · P " + hand.points().get(seat)
                                + " · T " + hand.totals().get(seat)).toList();
                equal("Browser score projection matches engine delta/total ordering " + players + "/" + contract,
                        expected, displayLines);
                equal("Browser score projection keeps all contributions " + players + "/" + contract,
                        hand.scoring().bySeat().stream().mapToLong(List::size).sum(),
                        table.view().scoreDetails().awards().lines().filter(line -> line.startsWith("  ")).count());
                if (contract == Contract.RENTZ) {
                    equal("Rentz has one zero in the displayed result " + players, 1L,
                            displayLines.stream().filter(line -> line.contains(" · P 0 · T ")).count());
                }
            }
        }
    }

    private static void browserFinalStandingsUseAndroidCompetitionTies() {
        var totals = List.of(-30, 20, 20, 0, -30, 5);
        var names = List.of("Ana", "Mihai", "Ioana", "Dan", "Eva", "Radu");
        equal("Web final standings keep Android competition ranks and total labels for tied totals", List.of(
                "1. Mihai · T 20", "1. Ioana · T 20", "3. Radu · T 5", "4. Dan · T 0",
                "5. Ana · T -30", "5. Eva · T -30"),
                RentzTable.finalStandings(totals, names));
        equal("Ranking projection does not reorder source totals", List.of(-30, 20, 20, 0, -30, 5), totals);
    }
    private static void profileAndAchievementProgressSurviveAdapterRecreation() {
        var name = new MemoryStore();
        var progress = new MemoryStore();
        var rankings = new MemoryStore();
        var first = RentzBrowser.localProfile(name, progress, rankings);
        var table = start(new RentzBrowser(() -> 111L), 3);
        equal("Actual complete achievement catalogue is adapted", AchievementCatalog.all().size(),
                catalogueCount(first.details(table)));
        first.rename(table, "Ana");
        equal("Renamed player reaches the running table", "Ana", table.names().get(0));
        equal("Profile writes the normalized local name", "Ana", first.details(table).name());
        table.act("reveal");
        first.completedAction(table);
        table.act("choose:RENTZ");
        first.completedAction(table);
        finishOneHand(table, first);
        String savedProgress = progress.value;
        truth("Confirmed hands persist achievement marks", savedProgress != null && savedProgress.contains("\n"));
        first.completedAction(table);
        equal("Duplicate callback is idempotent", savedProgress, progress.value);
        var restored = RentzBrowser.localProfile(name, progress, rankings);
        equal("Name survives profile recreation", "Ana", restored.details(table).name());
        equal("Full catalogue survives profile recreation", AchievementCatalog.all().size(),
                catalogueCount(restored.details(table)));
    }

    private static int catalogueCount(BrowserExperience.Details details) {
        var catalogue = (Map<?, ?>) details.catalogue();
        var groups = (List<?>) catalogue.get("groups");
        return groups.stream().map(group -> (Map<?, ?>) group).mapToInt(group -> ((List<?>) group.get("goals")).size()).sum();
    }

    private static void finishOneHand(RentzTable table) { finishOneHand(table, null); }

    private static void finishOneHand(RentzTable table, BrowserExperience profile) {
        for (int actionCount = 0; actionCount < 100; actionCount++) {
            if (table.completedHands().size() == 1) return;
            var actions = table.view().actions().stream().map(BrowserGame.Action::key).toList();
            if (actions.contains("auto-step")) table.act("auto-step");
            else if (actions.contains("reveal")) table.act("reveal");
            else if (actions.contains("choose:RENTZ")) table.act("choose:RENTZ");
            else if (actions.stream().anyMatch(key -> key.startsWith("card:"))) {
                table.act(actions.stream().filter(key -> key.startsWith("card:")).findFirst().orElseThrow());
            } else if (actions.contains("next")) throw new AssertionError("First hand ended but adapter exposed next");
            else throw new AssertionError("No legal adapter action: " + actions);
            if (profile != null) profile.completedAction(table);
        }
        throw new AssertionError("The first hand did not settle within the action bound");
    }

    private static RentzTable start(RentzBrowser browser, int players) {
        var config = new HashMap<String, String>();
        config.put("name", "Ana");
        config.put("players", Integer.toString(players));
        config.put("difficulty", "NORMAL");
        return (RentzTable) browser.start(config);
    }

    private static String firstLegalAction(RentzTable table) {
        return table.view().hand().stream().map(BrowserGame.Card::action).filter(action -> !action.isBlank())
                .findFirst().orElseThrow(() -> new AssertionError("Human Rentz turn has no exposed legal card"));
    }

    private static void everyTrickCardIsVisible() {
        var positions = new java.util.HashSet<Integer>();
        for (int players : List.of(3, 4, 5, 6)) for (var contract : Contract.values()) {
            if (contract == Contract.RENTZ) continue;
            for (boolean automatic : List.of(false, true)) {
                var table = start(new RentzBrowser(() -> 44119L + contract.ordinal()), players);
                table.act("reveal"); table.act("choose:" + contract.name());
                int commands = 0;
                while (table.observation().phase() == ro.rentzlab.core.RentzMatch.Phase.PLAY) {
                    var before = table.observation();
                    table.act(automatic ? "auto-step" : firstLegalAction(table));
                    var after = table.observation();
                    var events = after.events().subList(before.events().size(), after.events().size()).stream()
                            .filter(event -> event.action() == ro.rentzlab.core.RentzMatch.Action.PLAY).toList();
                    var frames = table.playback();
                    String context = players + "/" + contract + "/" + automatic + "/" + commands;
                    equal("Every accepted card has a frame including the human " + context,
                            events.stream().map(event -> event.seat() + ":" + event.card().label()).toList(),
                            frames.stream().map(frame -> frame.play().seat() + ":" + frame.play().faceLabel()).toList());
                    var trick = new java.util.ArrayList<>(before.trick());
                    for (int index = 0; index < frames.size(); index++) {
                        var frame = frames.get(index); var event = events.get(index);
                        if (event.seat() == 0 && players == 4) positions.add(trick.size());
                        equal("Only a new trick clears the table " + context, trick.isEmpty(), frame.startsTrick());
                        equal("Manual immediate, automatic marks actor first " + context,
                                event.seat() == 0 && !automatic ? 0 : RentzTable.AUTOMATED_ACTION_DELAY_MILLIS, (int) frame.delayMillis());
                        trick.add(new ro.rentzlab.core.TrickRules.Play(event.seat(), event.card()));
                        if (trick.size() < players) equal("No partial collection " + context, null, frame.collection());
                        else {
                            var cue = frame.collection(); truth("A full table collects " + context, cue != null);
                            equal("Every face collects in order " + context,
                                    trick.stream().map(play -> play.seat() + ":" + play.card().label()).toList(),
                                    cue.cards().stream().map(play -> play.seat() + ":" + play.faceLabel()).toList());
                            equal("Rules winner " + context, ro.rentzlab.core.TrickRules.winner(trick), cue.winningSeat());
                            var timing = ro.cardtable.ui.animation.TrickCollectionMotion.timing(
                                    ro.cardtable.ui.animation.TrickCollectionMotion.DISPLAY_MILLIS, 0);
                            equal("Shared Whist display " + context, ro.cardtable.ui.animation.TrickCollectionMotion.DISPLAY_MILLIS, cue.displayMillis());
                            equal("Shared Whist hold " + context, timing.delayMillis(), cue.holdMillis());
                            equal("Shared Whist travel " + context, timing.durationMillis(), cue.travelMillis());
                            trick.clear();
                        }
                    }
                    if (++commands > 64) throw new AssertionError("Hand did not settle " + context);
                }
            }
        }
        equal("Human first, middle and last", Set.of(0, 1, 2, 3), positions);
    }

    private static void equal(String label, Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(label + ": expected <" + expected + "> actual <" + actual + ">");
    }

    private static void truth(String label, boolean value) {
        checks++;
        if (!value) throw new AssertionError("Expected true: " + label);
    }

    private static void rejects(String label, Runnable action) {
        checks++;
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection: " + label);
    }

    private static final class MemoryStore implements RentzBrowserProfile.Store {
        private String value;
        @Override public String read() { return value; }
        @Override public void write(String next) { value = next; }
    }
}

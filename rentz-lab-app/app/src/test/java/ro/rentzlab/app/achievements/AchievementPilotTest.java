package ro.rentzlab.app.achievements;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.GameSession.Action;
import ro.rentzlab.app.GameSession.Origin;
import ro.rentzlab.app.SessionJson;
import ro.rentzlab.app.lifecycle.SessionRepository;
import ro.rentzlab.app.lifecycle.SessionStorage;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;
import ro.rentzlab.app.ui.profile.ProfileSettings;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;

@AndroidComponentLayer
public final class AchievementPilotTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    private static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    private static final Instant FIRST = Instant.parse("2026-11-09T20:55:00Z");
    private static final LocalProfile PROFILE = new LocalProfile(UUID.fromString("00000000-0000-4000-8000-000000000001"), ZONE);

    @Test public void catalogFollowsTheLearningPathAndCoversEachContract() {
        var badges = AchievementCatalog.badges();
        audit.equal("Complete level distribution", List.of(500L, 200L, 100L),
                java.util.Arrays.stream(AchievementCatalog.Level.values())
                        .map(level -> badges.stream().filter(b -> b.level() == level).count()).toList());
        audit.equal("Stable unique identifiers", (long) badges.size(), badges.stream().map(AchievementCatalog.Badge::id).distinct().count());
        audit.equal("Previous 240 stable identifiers are retained", 240L, badges.stream().filter(b -> !b.id().startsWith("s3-")).count());
        audit.equal("Start with the first choice", "first-choice", badges.get(0).id());
        audit.equal("Finish with mastery", AchievementCatalog.Stage.MASTERY, badges.get(badges.size() - 1).stage());
        audit.equal("Stages are monotone", badges.stream().map(b -> b.stage().ordinal()).sorted().toList(),
                badges.stream().map(b -> b.stage().ordinal()).toList());
        var contracts = EnumSet.noneOf(Contract.class);
        for (var badge : badges) if (badge.contract() != null) contracts.add(badge.contract());
        audit.equal("All eight contracts have their own goals", EnumSet.allOf(Contract.class), contracts);
    }

    @Test public void settledManualAndAssistedHandsUseDifferentEvidence() throws Exception {
        var manual = finish(Origin.MANUAL, false);
        var assisted = finish(Origin.MANUAL, true);
        var legacy = finish(Origin.UNKNOWN, false);
        audit.truth("Manual hand qualifies for manual participation", evidence(manual).manual());
        audit.truth("A mixed hand has both a human action and delegation", evidence(assisted).manualPlay() && evidence(assisted).delegated());
        audit.truth("Mixed hand cannot receive manual contract credit", !evidence(assisted).manual());
        audit.truth("Old journals do not invent manual provenance", !evidence(legacy).participated());
        var restored = SessionJson.restore(SessionJson.saved(assisted).toString());
        audit.equal("Command provenance survives codec and replay", assisted.achievementHands(), restored.achievementHands());
        var progress = AchievementProgress.empty(PROFILE).record(4, 31, evidence(restored), FIRST);
        audit.equal("Assisted onboarding unlocked", 1, progress.count(badge("assisted-hand")));
        audit.equal("Manual contract milestone stays locked", 0, progress.count(badge("rentz-manual")));
    }

    @Test public void progressCommitsAtomicallyAndSurvivesReplacementRestartAndProfileIsolation() throws Exception {
        var store = new Memory();
        var repository = new SessionRepository(store, PROFILE, Clock.fixed(FIRST, ZONE));
        var owner = repository.claim();
        owner.load();
        var newGame = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
        owner.commit(newGame);
        var settled = finish(Origin.MANUAL, false);
        store.reject = true;
        audit.rejects("Failed checkpoint does not award progress", IOException.class, () -> owner.commit(settled));
        audit.equal("No in-memory credit after a failed write", 0, owner.progress().count(badge("first-hand")));
        audit.equal("Durable journal still has no results", 0, SessionJson.restore(store.json).history().size());

        store.reject = false;
        owner.commit(settled);
        var first = owner.progress();
        owner.commit(settled);
        audit.equal("Recommit does not duplicate a completed hand", first, owner.progress());
        owner.commit(new GameSession(4, 71, RentzPlayer.Difficulty.NORMAL));
        var reopened = new SessionRepository(store, PROFILE, Clock.fixed(FIRST, ZONE)).claim();
        reopened.load();
        audit.equal("New match and restart keep unlocked badges", first, reopened.progress());
        var foreign = new SessionRepository(store, new LocalProfile(UUID.randomUUID(), ZONE), Clock.fixed(FIRST, ZONE)).claim();
        foreign.load();
        audit.equal("Other local identity receives no imported credit", Map.of(), foreign.progress().marks());
    }

    @Test public void localCalendarAndCanonicalDealsPreventRepeatedMasteryCredit() throws Exception {
        var hand = evidence(finish(Origin.MANUAL, false));
        var active = badge("active-days");
        var state = AchievementProgress.empty(PROFILE).record(4, 31, hand, FIRST);
        // 22:05 UTC is 00:05 the next day in this stored profile zone.
        var nextDay = Instant.parse("2026-11-09T22:05:00Z");
        state = state.record(4, 31 + (1L << 48), hand, nextDay);
        audit.equal("Alias of the same distribution does not add a day", 1, state.count(active));
        state = state.record(4, 32, hand, nextDay);
        audit.equal("Midnight follows the profile zone", 2, state.count(active));
        state = state.record(4, 33, hand, nextDay.plusSeconds(60)).record(4, 34, hand, FIRST.minusSeconds(86400));
        audit.equal("Same day and backward clock do not add credit", 2, state.count(active));
        state = state.record(4, 35, hand, nextDay.plusSeconds(86400));
        audit.equal("Three qualifying dates unlock mastery", 3, state.count(active));
    }

    @Test public void profileIdentitySurvivesDisplayNameChanges() {
        var values = new HashMap<String, String>();
        var store = new ProfileSettings.Store() {
            @Override public String get(String key, String fallback) { return values.getOrDefault(key, fallback); }
            @Override public void put(String key, String value) { values.put(key, value); }
        };
        var settings = new ProfileSettings(store);
        var identity = settings.localProfile();
        settings.name("Ana");
        audit.equal("Identity and timezone survive name edits", identity, new ProfileSettings(store).localProfile());
        audit.equal("Name is separately persisted", "Ana", new ProfileSettings(store).preferredName());
    }

    private static AchievementCatalog.Badge badge(String id) {
        return AchievementCatalog.badges().stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    private static AchievementHand evidence(GameSession session) { return session.achievementHands().get(0); }

    private static GameSession finish(Origin origin, boolean assist) {
        var session = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
        session.apply(new GameSession.Command(Action.CHOOSE, 0, null, Contract.RENTZ, false, origin));
        boolean first = true;
        for (int steps = 0; session.view().phase() == RentzMatch.Phase.PLAY; steps++) {
            if (steps > 128) throw new AssertionError("Hand did not settle");
            var view = session.view();
            if (view.turn() != 0) {
                session.advanceBotTurn();
            } else if (view.legal().isEmpty()) {
                session.apply(new GameSession.Command(Action.PASS, 0, null, null, false, Origin.AUTOMATIC));
            } else if (assist && !first) {
                session.advanceHumanTurn();
            } else {
                session.apply(new GameSession.Command(Action.PLAY, 0, view.legal().get(0), null, false, origin));
                first = false;
            }
        }
        return session;
    }

    private static final class Memory implements SessionStorage {
        String json;
        boolean reject;
        @Override public String read() { return json; }
        @Override public void write(String name, String value) throws IOException {
            if (reject) throw new IOException("Test write failure");
            if ("session.json".equals(name)) json = value;
        }
    }
}

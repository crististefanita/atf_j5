package ro.rentzlab.browser;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ro.cardtable.browser.BrowserExperience;
import ro.cardtable.browser.BrowserGame;
import ro.rentzlab.app.achievements.AchievementCatalog;
import ro.rentzlab.app.achievements.AchievementProgress;
import ro.rentzlab.app.profile.LocalProfile;
import ro.cardtable.names.PlayerNames;

/** Browser-local profile and achievement adapter over J5's real achievement rules. */
public final class RentzBrowserProfile implements BrowserExperience {
    public interface Store {
        String read();
        void write(String value);
    }

    public static final class Values {
        final Store name = new MemoryStore();
        final Store progress = new MemoryStore();
        final Store rankings = new MemoryStore();
    }

    private final Store name;
    private final Store progress;
    private final Store rankings;
    private Map<String, Object> pendingNotice;
    private String language = "ro";

    RentzBrowserProfile(Values values) { this(values.name, values.progress, values.rankings); }

    RentzBrowserProfile(Store name, Store progress, Store rankings) {
        this.name = name;
        this.progress = progress;
        this.rankings = rankings;
    }

    @Override public Details details(BrowserGame.Match match) { return details(match, "ro"); }

    @Override public synchronized Details details(BrowserGame.Match match, String language) {
        this.language = "en".equals(language) ? "en" : "ro";
        AchievementProgress current = currentProgress();
        String storedName = name.read();
        String displayName = storedName == null || storedName.isBlank() ? "Tu" : storedName;
        return new Details(displayName, storedName != null && !storedName.isBlank(), catalogue(current, language), pendingNotice);
    }

    @Override public synchronized void rename(BrowserGame.Match match, String value) {
        String normalized = value == null || value.isBlank() ? "" : PlayerNames.normalize(value);
        if (match instanceof RentzTable table && !normalized.isEmpty()) table.validateName(normalized);
        name.write(normalized);
        if (match instanceof RentzTable table) table.rename(normalized.isBlank() ? "Tu" : normalized);
    }

    @Override public synchronized void completedAction(BrowserGame.Match match) {
        if (!(match instanceof RentzTable table)) return;
        if (table.isFinished()) recordLocalResult(table);
        var hands = table.achievementHands();
        if (hands.isEmpty()) return;
        AchievementProgress previous = currentProgress();
        AchievementProgress next = previous;
        var saved = table.saved();
        for (var hand : hands) next = next.record(saved.players(), saved.seed(), hand, Instant.now());
        if (next == previous) return;
        writeProgress(next);
        var changes = next.gameplayChangesSince(previous);
        if (!changes.isEmpty()) {
            var goals = changes.stream().map(change -> goal(change.badge(), change.count(), language)).toList();
            pendingNotice = Map.of("title", "en".equals(language) ? "Achievement progress" : "Progres la realizări",
                    "summary", "en".equals(language) ? "Progress changed after the confirmed hand."
                            : "Progresul a fost actualizat după mâna confirmată.",
                    "goals", goals);
        }
    }

    @Override public synchronized void dismissNotice() { pendingNotice = null; }

    @Override public synchronized Rankings rankings(BrowserGame.Match match, String language) {
        var entries = decodeRankings(rankings.read()).stream()
                .map(row -> new RankEntry(row.name(), row.score(), row.ai(), false)).toList();
        String label = "en".equals(language) ? "Rentz results" : "Rezultate Rentz";
        return new Rankings("rentz", List.of(new RankBoard("rentz", label, entries)));
    }

    private void recordLocalResult(RentzTable table) {
        String key = table.saved().players() + ":" + table.saved().difficulty().name() + ":" + table.saved().seed();
        var values = new ArrayList<>(decodeRankings(rankings.read()));
        if (values.stream().anyMatch(entry -> entry.match().equals(key))) return;
        var result = table.finalResults();
        for (int seat = 0; seat < result.size(); seat++) {
            String player = table.names().get(seat);
            boolean ai = seat != 0;
            values.add(new StoredRank(key, player, result.get(seat), ai));
        }
        values.sort(java.util.Comparator.comparingInt(StoredRank::score).reversed().thenComparing(StoredRank::name));
        writeRankings(values.stream().limit(40).toList());
    }

    private record StoredRank(String match, String name, int score, boolean ai) { }

    private List<StoredRank> decodeRankings(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        var result = new ArrayList<StoredRank>();
        for (String row : encoded.split("\\n")) {
            String[] fields = row.split("\\t", -1);
            if (fields.length != 4) continue;
            try {
                result.add(new StoredRank(decode(fields[0]), decode(fields[1]), Integer.parseInt(fields[2]),
                        Boolean.parseBoolean(fields[3])));
            } catch (RuntimeException invalid) { /* Ignore only the damaged row. */ }
        }
        return List.copyOf(result);
    }

    private void writeRankings(List<StoredRank> entries) {
        rankings.write(entries.stream().map(entry -> encode(entry.match()) + "\t" + encode(entry.name()) + "\t"
                + entry.score() + "\t" + entry.ai()).collect(java.util.stream.Collectors.joining("\n")));
    }

    private AchievementProgress currentProgress() {
        String value = progress.read();
        if (value == null || value.isBlank()) {
            var empty = AchievementProgress.empty(new LocalProfile(UUID.randomUUID(), ZoneId.of("UTC")));
            writeProgress(empty);
            return empty;
        }
        String[] lines = value.split("\\n");
        if (lines.length == 0) throw new IllegalArgumentException("Empty browser achievement profile");
        String[] header = lines[0].split("\\t", -1);
        if (header.length != 3 || !"v1".equals(header[0])) throw new IllegalArgumentException("Unsupported browser achievement profile");
        var profile = new LocalProfile(UUID.fromString(header[1]), ZoneId.of(header[2]));
        var marks = new LinkedHashMap<String, List<AchievementProgress.Mark>>();
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].isBlank()) continue;
            String[] fields = lines[index].split("\\t", -1);
            if (fields.length != 3) throw new IllegalArgumentException("Invalid achievement mark");
            String id = decode(fields[0]);
            var mark = new AchievementProgress.Mark(decode(fields[1]), Instant.ofEpochMilli(Long.parseLong(fields[2])));
            marks.computeIfAbsent(id, ignored -> new ArrayList<>()).add(mark);
        }
        return new AchievementProgress(profile, marks);
    }

    private void writeProgress(AchievementProgress value) {
        var lines = new ArrayList<String>();
        lines.add("v1\t" + value.profile().id() + "\t" + value.profile().zone().getId());
        value.marks().forEach((id, marks) -> marks.forEach(mark -> lines.add(encode(id) + "\t" + encode(mark.key())
                + "\t" + mark.at().toEpochMilli())));
        progress.write(String.join("\n", lines));
    }

    private Map<String, Object> catalogue(AchievementProgress current, String language) {
        boolean ro = !"en".equals(language);
        var groups = new ArrayList<Map<String, Object>>();
        int total = 0;
        int unlocked = 0;
        for (var stage : AchievementCatalog.Stage.values()) {
            var goals = AchievementCatalog.all().stream().filter(badge -> badge.stage() == stage)
                    .map(badge -> goal(badge, current.count(badge), language)).toList();
            int complete = (int) goals.stream().filter(goal -> Boolean.TRUE.equals(goal.get("complete"))).count();
            total += goals.size();
            unlocked += complete;
            String heading = switch (stage) {
                case LEARN -> ro ? "Învață jocul" : "Learn the game";
                case CONTRACTS -> ro ? "Stăpânește sub-jocurile" : "Master the contracts";
                case TACTICS -> ro ? "Aplică tactici" : "Apply tactics";
                case MASTERY -> ro ? "Dovedește măiestria" : "Prove mastery";
            };
            groups.add(Map.of("number", groups.size() + 1, "heading", heading, "recommended", complete < goals.size(), "goals", goals));
        }
        return Map.of("summary", (ro ? "Deblocate " : "Unlocked ") + unlocked + "/" + total,
                "groups", groups);
    }

    private Map<String, Object> goal(AchievementCatalog.Badge badge, int count, String language) {
        boolean ro = !"en".equals(language);
        boolean complete = count >= badge.goal();
        String level = switch (badge.level()) {
            case EASY -> ro ? "Ușor" : "Easy";
            case MEDIUM -> ro ? "Mediu" : "Medium";
            case HARD -> ro ? "Greu" : "Hard";
        };
        return Map.of("id", badge.id(), "marker", complete ? "✓" : "○", "level", level,
                "title", ro ? badge.romanian() : badge.english(), "progress", count + "/" + badge.goal(),
                "description", ro ? badge.ruleRo() : badge.ruleEn(), "complete", complete);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static final class MemoryStore implements Store {
        private String value;
        @Override public String read() { return value; }
        @Override public void write(String next) { value = next; }
    }
}

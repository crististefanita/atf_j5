package ro.rentzlab.app.achievements;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ro.rentzlab.core.deal.DealSeed;

public record AchievementProgress(LocalProfile profile, Map<String, List<Mark>> marks) {
    public record Mark(String key, Instant at) {
        public Mark { Objects.requireNonNull(key); Objects.requireNonNull(at); }
    }

    public AchievementProgress {
        Objects.requireNonNull(profile);
        var copy = new LinkedHashMap<String, List<Mark>>();
        marks.forEach((id, rows) -> copy.put(id, List.copyOf(rows)));
        marks = java.util.Collections.unmodifiableMap(copy);
    }

    public static AchievementProgress empty(LocalProfile profile) {
        return new AchievementProgress(profile, Map.of());
    }

    public int count(AchievementCatalog.Badge badge) { return marks.getOrDefault(badge.id(), List.of()).size(); }

    public AchievementProgress record(int players, long seed, AchievementHand hand, Instant at) {
        return record(players, seed, hand, at, AchievementCatalog.all());
    }

    AchievementProgress recordGameplay(int players, long seed, AchievementHand hand, Instant at) {
        return record(players, seed, hand, at, GameplayAchievementCatalog.badges());
    }

    private AchievementProgress record(int players, long seed, AchievementHand hand, Instant at,
            List<AchievementCatalog.Badge> catalog) {
        String match = players + ":" + DealSeed.effective(seed);
        var next = new LinkedHashMap<>(marks);
        for (var badge : catalog) {
            var before = marks.getOrDefault(badge.id(), List.of());
            if (before.size() >= badge.goal() || !badge.accepts(hand)) continue;
            String key = switch (badge.unit()) {
                case HAND -> match + ":" + hand.result().number();
                case CONTRACT -> hand.result().contract().name();
                case TABLE -> Integer.toString(players);
                case MATCH, DAY -> match;
            };
            if (before.stream().anyMatch(mark -> mark.key().equals(key))) continue;
            if (badge.unit() == AchievementCatalog.Unit.DAY && !before.isEmpty()
                    && !day(at).isAfter(day(before.get(before.size() - 1).at()))) continue;
            var after = new ArrayList<>(before);
            after.add(new Mark(key, at));
            next.put(badge.id(), after);
        }
        return next.equals(marks) ? this : new AchievementProgress(profile, next);
    }

    private java.time.LocalDate day(Instant at) { return at.atZone(profile.zone()).toLocalDate(); }
}

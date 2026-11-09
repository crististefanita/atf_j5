package ro.rentzlab.app.ui.screens;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import ro.rentzlab.app.achievements.AchievementCatalog;
import ro.rentzlab.app.achievements.AchievementCatalog.Badge;
import ro.rentzlab.app.achievements.AchievementCatalog.Level;
import ro.rentzlab.app.achievements.AchievementCatalog.PlayMode;
import ro.rentzlab.app.achievements.AchievementHand;
import ro.rentzlab.app.achievements.AchievementProgress;
import ro.rentzlab.app.achievements.GameplayAchievementCatalog;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.core.Contract;

/** Presentation shared by the Android browser and the component sanity fixtures. */
public final class AchievementBrowser {
    public enum Status { LOCKED, IN_PROGRESS, UNLOCKED }
    public enum Collection { GAMEPLAY, TRAINING }

    public record Query(String text, Level level, Contract contract, Status status, PlayMode mode) {
        public static Query empty() { return new Query("", null, null, null, null); }
        public boolean active() { return !equals(empty()); }
    }

    public record Row(long itemId, Badge badge, String title, String subtitle, int count, Status status) {
        boolean heading() { return badge == null; }
    }

    public record Details(String title, String progress, String rule, String context, String latest) { }

    private record Entry(long id, Badge badge, String searchable) { }
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final List<Entry> TRAINING = index(AchievementCatalog.badges());
    private static final List<Entry> GAMEPLAY = index(GameplayAchievementCatalog.badges());
    private final Collection collection;
    private final List<Entry> entries;
    private final RentzUiText copy;
    private final AchievementProgress progress;
    private final AchievementHand latest;

    public AchievementBrowser(RentzUiText copy, AchievementProgress progress, AchievementHand latest) {
        this(copy, progress, latest, Collection.TRAINING);
    }

    public AchievementBrowser(RentzUiText copy, AchievementProgress progress, AchievementHand latest, Collection collection) {
        this.copy = copy;
        this.progress = progress;
        this.latest = latest;
        this.collection = collection;
        this.entries = collection == Collection.GAMEPLAY ? GAMEPLAY : TRAINING;
    }

    public int total() { return entries.size(); }
    public long unlocked() {
        return entries.stream().filter(entry -> status(entry.badge()) == Status.UNLOCKED).count();
    }

    public String recommendation() {
        return entries.stream().map(Entry::badge).filter(badge -> status(badge) != Status.UNLOCKED)
                .findFirst().map(this::title).orElse(copy.text("All achievements unlocked", "Toate realizările sunt deblocate"));
    }

    public List<Row> rows(Query query) {
        var words = normalize(query.text()).split("\\s+");
        var rows = new ArrayList<Row>();
        AchievementCatalog.Stage previous = null;
        for (var entry : entries) {
            var badge = entry.badge();
            if (!matches(query, words, entry)) continue;
            if (previous != badge.stage()) {
                previous = badge.stage();
                rows.add(new Row(-1L - previous.ordinal(), null, heading(previous), "", 0, null));
            }
            int count = count(badge);
            var status = status(badge);
            rows.add(new Row(entry.id(), badge, title(badge), summary(badge, count, status), count, status));
        }
        return List.copyOf(rows);
    }

    private boolean matches(Query query, String[] words, Entry entry) {
        var badge = entry.badge();
        return (query.level() == null || query.level() == badge.level())
                && (query.contract() == null || query.contract() == badge.contract())
                && (query.status() == null || query.status() == status(badge))
                && (query.mode() == null || query.mode() == badge.mode() || badge.mode() == PlayMode.EITHER
                        || query.mode() == PlayMode.MANUAL && badge.mode() == PlayMode.MANUAL_MATCH)
                && Arrays.stream(words).allMatch(entry.searchable()::contains);
    }

    public Details details(Badge badge) {
        var criteria = GameplayAchievementCatalog.targetCriteria(badge, copy::contract, copy.language() == RentzUiText.Language.RO);
        var rule = copy.text(badge.ruleEn(), badge.ruleRo());
        if (!criteria.isEmpty()) rule += "\n\n" + criteria;
        return new Details(title(badge), summary(badge, count(badge), status(badge)),
                rule, mode(badge.mode()) + " · " + unit(badge.unit()),
                switch (badge.eligibility(latest)) {
                    case NO_HAND -> copy.text("No completed hand in the current match.", "Nu există încă o mână încheiată în partida curentă.");
                    case OTHER_CONTRACT -> copy.text("The last hand used another contract.", "Ultima mână a avut alt contract.");
                    case MATCH_IN_PROGRESS -> copy.text("The final standings are not available yet.", "Clasamentul final nu este încă disponibil.");
                    case NEED_MANUAL -> badge.mode() == PlayMode.MANUAL_MATCH
                            ? copy.text("The match contains delegated or unverified decisions.", "Partida conține decizii delegate sau neverificate.")
                            : copy.text("Manual play could not be confirmed for the whole hand.", "Jocul manual nu a fost confirmat pentru întreaga mână.");
                    case NEED_ASSISTANCE -> copy.text("Auto play made no decision for you.", "Jocul automat nu a decis pentru tine.");
                    case CONTEXT -> copy.text("The last hand did not meet the context described above.", "Ultima mână nu a avut contextul descris mai sus.");
                    case TACTIC -> copy.text("The last hand did not meet the stated objective.", "Ultima mână nu a îndeplinit obiectivul indicat.");
                    case ELIGIBLE -> copy.text("The last hand met the play conditions. Each distinct proof counts once.",
                            "Ultima mână a îndeplinit condițiile de joc. Fiecare dovadă distinctă se numără o singură dată.");
                });
    }

    public String level(Level level) {
        return switch (level) {
            case EASY -> copy.text("Easy", "Ușor");
            case MEDIUM -> copy.text("Medium", "Mediu");
            case HARD -> copy.text("Hard", "Greu");
        };
    }

    public String statusLabel(Status status) {
        return switch (status) {
            case LOCKED -> copy.text("Locked", "Blocată");
            case IN_PROGRESS -> copy.text("In progress", "În progres");
            case UNLOCKED -> copy.text("Unlocked", "Deblocată");
        };
    }

    public String mode(PlayMode mode) {
        return switch (mode) {
            case MANUAL -> copy.text("Manual play", "Joc manual");
            case MANUAL_MATCH -> copy.text("Entire match played manually", "Partidă jucată integral manual");
            case ASSISTED -> copy.text("Auto play required", "Joc automat necesar");
            case EITHER -> copy.text("Manual or assisted", "Manual sau asistat");
        };
    }

    private String unit(AchievementCatalog.Unit unit) {
        return switch (unit) {
            case HAND -> copy.text("Distinct completed hands", "Mâini încheiate distincte");
            case CONTRACT -> copy.text("Distinct contracts", "Contracte distincte");
            case TABLE -> copy.text("Distinct table sizes", "Mărimi de masă distincte");
            case MATCH -> copy.text("Distinct completed matches", "Partide complete distincte");
            case DAY -> copy.text("Distinct days and matches", "Zile și partide distincte");
        };
    }

    private int count(Badge badge) { return progress == null ? 0 : progress.count(badge); }

    private Status status(Badge badge) {
        int count = count(badge);
        return count >= badge.goal() ? Status.UNLOCKED : count == 0 ? Status.LOCKED : Status.IN_PROGRESS;
    }

    private String title(Badge badge) {
        var title = copy.text(badge.english(), badge.romanian());
        return badge.contract() == null ? title : copy.contract(badge.contract()) + " · " + title;
    }

    private String summary(Badge badge, int count, Status status) {
        return level(badge.level()) + " · " + statusLabel(status) + " " + count + "/" + badge.goal();
    }

    private String heading(AchievementCatalog.Stage stage) {
        if (collection == Collection.GAMEPLAY) return switch (stage) {
            case LEARN -> copy.text("Easy · First hands", "Ușor · Primele mâini");
            case TACTICS -> copy.text("Medium · Complete matches", "Mediu · Partide complete");
            case MASTERY -> copy.text("Hard · Mastery", "Greu · Măiestrie");
            case CONTRACTS -> throw new IllegalStateException("Gameplay uses difficulty sections");
        };
        return switch (stage) {
            case LEARN -> copy.text("1 · Learn the table", "1 · Învață masa");
            case CONTRACTS -> copy.text("2 · Understand the contracts", "2 · Înțelege contractele");
            case TACTICS -> copy.text("3 · Choose your tactics", "3 · Alege tactica");
            case MASTERY -> copy.text("4 · Master the game", "4 · Stăpânește jocul");
        };
    }

    private static List<Entry> index(List<Badge> catalog) {
        var en = new RentzUiText(RentzUiText.Language.EN);
        var ro = new RentzUiText(RentzUiText.Language.RO);
        var entries = new ArrayList<Entry>();
        for (var badge : catalog) {
            var contract = badge.contract() == null ? "" : en.contract(badge.contract()) + " " + ro.contract(badge.contract());
            var searchable = normalize(String.join(" ", contract, badge.english(), badge.romanian(), badge.ruleEn(), badge.ruleRo()));
            entries.add(new Entry(entries.size() + 1L, badge, searchable));
        }
        return List.copyOf(entries);
    }

    private static String normalize(String value) {
        return MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }
}

package ro.rentzlab.app.achievements;

import static ro.rentzlab.app.achievements.AchievementCatalog.*;
import static ro.rentzlab.app.achievements.AchievementTactics.Fact.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import ro.rentzlab.ai.RentzPlayer.Difficulty;
import ro.rentzlab.app.achievements.AchievementExpansion.Tactic;
import ro.rentzlab.core.Contract;

/** Stable recipes for the final catalog. Simulation runs in the component suite, never on-device. */
final class AchievementFullCatalog {
    enum Context {
        ANY("", "", h -> true),
        THREE("3 players", "3 jucători", h -> h.players() == 3),
        FOUR("4 players", "4 jucători", h -> h.players() == 4),
        FIVE("5 players", "5 jucători", h -> h.players() == 5),
        SIX("6 players", "6 jucători", h -> h.players() == 6),
        SMALL("3–4 players", "3–4 jucători", h -> h.players() <= 4),
        LARGE("5–6 players", "5–6 jucători", h -> h.players() >= 5),
        NORMAL("Normal opponents", "Adversari Normal", h -> h.difficulty() == Difficulty.NORMAL),
        HARD("Hard opponents", "Adversari Greu", h -> h.difficulty() == Difficulty.HARD),
        PERSONAL("Your choice", "Alegerea ta", AchievementHand::manualChoice),
        OPPONENT("Opponent's choice", "Alegerea unui adversar", h -> h.result().chooser() != 0),
        OPEN_CHOICE("Open contract", "Contract la vedere", h -> !h.result().blind()),
        BLIND("Blind contract", "Contract pe nevăzute", h -> h.result().blind());

        final String en, ro;
        final Predicate<AchievementHand> applies;
        Context(String en, String ro, Predicate<AchievementHand> applies) {
            this.en = en;
            this.ro = ro;
            this.applies = applies;
        }
    }

    private record Step(Context context, int goal) { }
    record ContractRule(Contract contract, int tacticIndex, Tactic tactic, Level level, Context context, int goal) {
        Unit unit() { return level == Level.HARD ? Unit.DAY : Unit.HAND; }
        String id() { return "s3-" + key(contract) + "-" + key(tactic.fact()) + "-" + key(context) + "-" + key(unit()) + "-" + goal; }
        Badge badge() {
            return create(id(), level, contract, unit(), goal, tactic.en(), tactic.ro(), PlayMode.MANUAL,
                    context, hand -> hand.tactics().has(tactic.fact()));
        }
    }

    private static final List<Context> TABLES = List.of(Context.THREE, Context.FOUR, Context.FIVE, Context.SIX);
    private static final List<Contract> ORDER = List.of(Contract.TRICKS, Contract.QUEENS, Contract.DIAMONDS,
            Contract.KING_HEARTS, Contract.TOTALS, Contract.WHIST, Contract.TEN_CLUBS, Contract.RENTZ);
    private static final List<Step> EASY = List.of(
            new Step(Context.THREE, 1), new Step(Context.FOUR, 1), new Step(Context.FIVE, 1), new Step(Context.SIX, 1),
            new Step(Context.THREE, 2), new Step(Context.FOUR, 2), new Step(Context.FIVE, 2), new Step(Context.SIX, 2),
            new Step(Context.SMALL, 1), new Step(Context.LARGE, 1), new Step(Context.OPEN_CHOICE, 1), new Step(Context.BLIND, 1),
            new Step(Context.NORMAL, 1), new Step(Context.HARD, 1), new Step(Context.OPPONENT, 1));
    private static final List<Step> MEDIUM = List.of(
            new Step(Context.ANY, 3), new Step(Context.NORMAL, 3), new Step(Context.HARD, 3),
            new Step(Context.PERSONAL, 3), new Step(Context.OPPONENT, 3), new Step(Context.LARGE, 4),
            new Step(Context.SMALL, 4), new Step(Context.OPEN_CHOICE, 4), new Step(Context.BLIND, 3));
    private static final List<Step> HARD = List.of(
            new Step(Context.ANY, 3), new Step(Context.HARD, 4), new Step(Context.NORMAL, 4),
            new Step(Context.LARGE, 4), new Step(Context.SMALL, 4), new Step(Context.OPEN_CHOICE, 4));

    private AchievementFullCatalog() { }

    static List<ContractRule> candidates(Contract contract, Level level) {
        var tactics = AchievementExpansion.tactics(contract);
        var steps = switch (level) { case EASY -> EASY; case MEDIUM -> MEDIUM; case HARD -> HARD; };
        var rules = new ArrayList<ContractRule>();
        for (var step : steps) {
            for (int i = 0; i < tactics.size(); i++) {
                var rule = new ContractRule(contract, i, tactics.get(i), level, step.context(), step.goal());
                if (!alreadyReleased(rule)) rules.add(rule);
            }
        }
        return List.copyOf(rules);
    }

    private static boolean alreadyReleased(ContractRule rule) {
        int i = rule.tacticIndex(), goal = rule.goal();
        var context = rule.context();
        int primary = AchievementExpansion.primaryIndex(rule.contract());
        return switch (rule.level()) {
            case EASY -> goal == 1 && context == TABLES.get((ORDER.indexOf(rule.contract()) + i) % TABLES.size());
            case MEDIUM -> (i == primary && ((goal == 3 && (context == Context.PERSONAL || context == Context.HARD))
                    || (goal == 4 && (context == Context.OPPONENT || context == Context.LARGE))))
                    || (i == 2 && goal == 3 && context == Context.NORMAL)
                    || (i == 4 && goal == 3 && context == Context.BLIND)
                    || (goal == 3 && context == Context.PERSONAL
                    && AchievementExpansion.coveredByPilot(rule.contract(), rule.tactic().fact()));
            case HARD -> i == 0 && ((goal == 3 && context == Context.ANY) || (goal == 4 && context == Context.HARD));
        };
    }

    static void contract(List<Badge> rows, Contract contract, Level level) {
        int count = switch (level) {
            case EASY -> switch (contract) {
                case RENTZ -> 29;
                case KING_HEARTS, TEN_CLUBS -> 34;
                default -> 31;
            };
            case MEDIUM -> contract == Contract.KING_HEARTS ? 10 : 13;
            case HARD -> switch (contract) {
                case RENTZ -> 7;
                case TEN_CLUBS -> 5;
                default -> 8;
            };
        };
        var selected = candidates(contract, level).stream()
                .filter(AchievementRateFloor::allows)
                .limit(count)
                .sorted(Comparator.comparingInt(ContractRule::tacticIndex).thenComparingInt(ContractRule::goal))
                .collect(Collectors.toList());
        if (selected.size() != count) throw new IllegalStateException("Insufficient calibrated rules for " + contract + "/" + level);
        for (var rule : selected) rows.add(rule.badge());
    }

    private record Lesson(String id, String en, String ro, PlayMode mode, Predicate<AchievementHand> proof) { }
    private static final List<Lesson> LESSONS = List.of(
            new Lesson("at-table", "Finish with manual play", "Încheie prin joc manual", PlayMode.MANUAL, h -> true),
            new Lesson("choose", "Choose the contract yourself", "Alege personal contractul", PlayMode.MANUAL, AchievementHand::manualChoice),
            new Lesson("assist", "Finish with Auto play", "Încheie cu joc automat", PlayMode.ASSISTED, h -> true),
            new Lesson("revealed", "Choose after revealing your hand", "Alege după ce vezi cărțile", PlayMode.MANUAL,
                    h -> h.manualChoice() && !h.result().blind()),
            new Lesson("blind", "Choose before revealing your hand", "Alege înainte să vezi cărțile", PlayMode.MANUAL,
                    h -> h.manualChoice() && h.result().blind()),
            new Lesson("target", "Meet the contract target", "Atinge ținta contractului", PlayMode.MANUAL, AchievementCatalog::target),
            new Lesson("follow", "Follow the led suit", "Urmează culoarea cerută", PlayMode.MANUAL, h -> h.tactics().has(FOLLOWED_SUIT)),
            new Lesson("discard", "Let a legal off-suit discard go", "Lasă unui adversar o aruncare în afara culorii", PlayMode.MANUAL,
                    h -> h.tactics().has(SAFE_DISCARD) && avoidance(h.result().contract())),
            new Lesson("standings", "Reach the upper half of the standings", "Ajungi în prima jumătate a clasamentului",
                    PlayMode.MANUAL, AchievementFullCatalog::upperHalf),
            new Lesson("two-ends", "Extend both ends in one Rentz hand", "Extinde ambele capete într-o mână Rentz", PlayMode.MANUAL,
                    h -> h.result().contract() == Contract.RENTZ && h.tactics().has(EXTEND_LOW) && h.tactics().has(EXTEND_HIGH)));

    private static final Lesson FINISH = new Lesson("complete", "Complete a match", "Încheie o partidă", PlayMode.EITHER,
            h -> h.complete() && h.participated());
    private static final Lesson PODIUM = new Lesson("upper-finish", "Finish in the upper half", "Termină în prima jumătate",
            PlayMode.MANUAL, h -> h.complete() && upperHalf(h));

    static void crossContract(List<Badge> rows, Level level) {
        for (var lesson : LESSONS) {
            switch (level) {
                case EASY -> easyLessons(rows, lesson);
                case MEDIUM -> mediumLessons(rows, lesson);
                case HARD -> hardLessons(rows, lesson);
            }
        }
        if (level == Level.MEDIUM) {
            for (var lesson : List.of(FINISH, PODIUM)) {
                lesson(rows, lesson, level, Unit.MATCH, 2, Context.ANY);
                lesson(rows, lesson, level, Unit.MATCH, 1, Context.NORMAL);
                lesson(rows, lesson, level, Unit.MATCH, 1, Context.HARD);
            }
        }
        if (level == Level.HARD) {
            lesson(rows, FINISH, level, Unit.DAY, 3, Context.ANY);
            lesson(rows, PODIUM, level, Unit.DAY, 3, Context.ANY);
        }
    }

    private static void easyLessons(List<Badge> rows, Lesson lesson) {
        lesson(rows, lesson, Level.EASY, Unit.HAND, 2, Context.ANY);
        lesson(rows, lesson, Level.EASY, Unit.HAND, 3, Context.ANY);
        if (!lesson.id().equals("two-ends"))
            for (var table : TABLES) lesson(rows, lesson, Level.EASY, Unit.HAND, 1, table);
        lesson(rows, lesson, Level.EASY, Unit.HAND, 1, Context.NORMAL);
        lesson(rows, lesson, Level.EASY, Unit.HAND, 1, Context.HARD);
        if (!lesson.id().equals("standings")) {
            lesson(rows, lesson, Level.EASY, Unit.HAND, 2, Context.SMALL);
            lesson(rows, lesson, Level.EASY, Unit.HAND, 2, Context.LARGE);
        }
    }

    private static void mediumLessons(List<Badge> rows, Lesson lesson) {
        lesson(rows, lesson, Level.MEDIUM, Unit.TABLE, 3, Context.ANY);
        if (lesson.id().equals("two-ends")) lesson(rows, lesson, Level.MEDIUM, Unit.HAND, 4, Context.ANY);
        else lesson(rows, lesson, Level.MEDIUM, Unit.CONTRACT, 3, Context.ANY);
        lesson(rows, lesson, Level.MEDIUM, Unit.HAND, 5, Context.HARD);
    }

    private static void hardLessons(List<Badge> rows, Lesson lesson) {
        lesson(rows, lesson, Level.HARD, Unit.DAY, 5, Context.ANY);
        if (List.of("at-table", "choose", "target", "follow", "standings").contains(lesson.id()))
            lesson(rows, lesson, Level.HARD, Unit.DAY, 5, Context.HARD);
    }

    private static void lesson(List<Badge> rows, Lesson lesson, Level level, Unit unit, int goal, Context context) {
        String id = "s3-cross-" + lesson.id() + "-" + key(context) + "-" + key(unit) + "-" + goal;
        rows.add(create(id, level, null, unit, goal, lesson.en(), lesson.ro(), lesson.mode(), context, lesson.proof()));
    }

    private static boolean upperHalf(AchievementHand hand) {
        var totals = hand.result().totals();
        long ahead = totals.stream().filter(value -> value > totals.get(0)).count();
        return ahead < totals.size() / 2;
    }

    private static boolean avoidance(Contract contract) {
        return switch (contract) {
            case TRICKS, QUEENS, DIAMONDS, KING_HEARTS, TOTALS -> true;
            case WHIST, TEN_CLUBS, RENTZ -> false;
        };
    }

    private static Badge create(String id, Level level, Contract contract, Unit unit, int goal,
            String en, String ro, PlayMode mode, Context context, Predicate<AchievementHand> proof) {
        String qualifierEn = qualifier(context.en, unit, goal, false);
        String qualifierRo = qualifier(context.ro, unit, goal, true);
        var stage = switch (level) {
            case EASY -> contract == null ? Stage.TACTICS : Stage.CONTRACTS;
            case MEDIUM -> Stage.TACTICS;
            case HARD -> Stage.MASTERY;
        };
        return new Badge(id, stage, level, contract, unit, goal,
                en + " · " + qualifierEn, ro + " · " + qualifierRo,
                en + ". " + qualifierEn + ".", ro + ". " + qualifierRo + ".", mode, context.applies, proof);
    }

    private static String qualifier(String context, Unit unit, int goal, boolean ro) {
        String unitLabel = switch (unit) {
            case HAND -> ro ? (goal == 1 ? "mână" : "mâini") : (goal == 1 ? "hand" : "hands");
            case CONTRACT -> ro ? "contracte" : "contracts";
            case TABLE -> ro ? "mărimi de masă" : "table sizes";
            case MATCH -> ro ? (goal == 1 ? "partidă" : "partide") : (goal == 1 ? "match" : "matches");
            case DAY -> ro ? "zile distincte" : "distinct days";
        };
        return (context.isEmpty() ? "" : context + " · ") + goal + " " + unitLabel;
    }

    private static String key(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
}

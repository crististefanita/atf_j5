package ro.rentzlab.app.achievements;

import static ro.rentzlab.app.achievements.AchievementCatalog.*;
import static ro.rentzlab.app.achievements.AchievementTactics.Fact.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import ro.rentzlab.ai.RentzPlayer.Difficulty;
import ro.rentzlab.core.Contract;

/** Gameplay milestones share the atomic evidence store with the training catalog. */
public final class GameplayAchievementCatalog {
    enum Metric {
        HAND(Unit.HAND), CHOICE(Unit.HAND), TARGET(Unit.HAND), CONTRACT(Unit.CONTRACT),
        RENTZ_SEQUENCE(Unit.HAND), MATCH_FINISHED(Unit.MATCH), MATCH_WIN(Unit.MATCH),
        CONTRACT_TARGET(Unit.CONTRACT), RENTZ_TOP_TWO(Unit.HAND), MATCH_PODIUM(Unit.MATCH),
        RENTZ_LARGE_FIRST(Unit.HAND), CLEAN_TOTALS(Unit.HAND), HARD_LARGE_WIN(Unit.MATCH);

        final Unit unit;
        Metric(Unit unit) { this.unit = unit; }

        boolean supportsTable(int players, Difficulty difficulty) {
            return switch (this) {
                case RENTZ_LARGE_FIRST -> players >= 5;
                case HARD_LARGE_WIN -> players >= 5 && difficulty == Difficulty.HARD;
                default -> true;
            };
        }

        boolean coversContract(Contract contract) {
            return switch (this) {
                case RENTZ_SEQUENCE, RENTZ_TOP_TWO, RENTZ_LARGE_FIRST -> contract == Contract.RENTZ;
                case CLEAN_TOTALS -> contract == Contract.TOTALS;
                case CONTRACT, TARGET, CONTRACT_TARGET -> true;
                default -> false;
            };
        }


        boolean matches(AchievementHand hand) {
            return switch (this) {
                case HAND, CONTRACT -> true;
                case CHOICE -> hand.manualChoice();
                case TARGET, CONTRACT_TARGET -> AchievementCatalog.target(hand);
                case RENTZ_SEQUENCE -> isRentz(hand) && (hand.tactics().has(EXTEND_LOW) || hand.tactics().has(EXTEND_HIGH));
                case MATCH_FINISHED -> hand.complete();
                case MATCH_WIN, HARD_LARGE_WIN -> hand.complete() && rank(hand) == 1;
                case RENTZ_TOP_TWO -> isRentz(hand) && hand.tactics().has(TOP_TWO);
                case MATCH_PODIUM -> hand.complete() && rank(hand) <= Math.min(3, hand.players() - 1);
                case RENTZ_LARGE_FIRST -> isRentz(hand) && AchievementCatalog.target(hand);
                case CLEAN_TOTALS -> hand.result().contract() == Contract.TOTALS && hand.tactics().has(NO_TRICKS);
            };
        }
    }

    record Rule(String id, Level level, Metric metric, int target, String titleEn, String titleRo,
            String ruleEn, String ruleRo) {
        Badge badge() {
            var stage = switch (level) {
                case EASY -> Stage.LEARN;
                case MEDIUM -> Stage.TACTICS;
                case HARD -> Stage.MASTERY;
            };
            return new Badge(id, stage, level, null, metric.unit, target, titleEn, titleRo, ruleEn, ruleRo,
                    metric.unit == Unit.MATCH ? PlayMode.MANUAL_MATCH : PlayMode.MANUAL,
                    h -> metric.supportsTable(h.players(), h.difficulty()), metric::matches);
        }
    }

    private static final List<Rule> RULES = GameplayAchievementCatalogData.rules();
    private static final List<Badge> BADGES = create();
    private GameplayAchievementCatalog() { }

    public static List<Badge> badges() { return BADGES; }

    public static boolean supportsTable(Badge badge, int players, Difficulty difficulty) {
        return metricFor(badge).supportsTable(players, difficulty);
    }

    public static boolean coversContract(Badge badge, Contract contract) {
        return metricFor(badge).coversContract(contract);
    }

    private static Metric metricFor(Badge badge) {
        return RULES.stream().filter(rule -> rule.id().equals(badge.id())).findFirst().orElseThrow().metric();
    }

    private static List<Badge> create() {
        var rows = new ArrayList<Badge>();
        for (var rule : RULES) rows.add(rule.badge());
        return List.copyOf(rows);
    }

    public static String targetCriteria(Badge badge, Function<Contract, String> label, boolean romanian) {
        for (var rule : RULES) {
            if (!rule.id().equals(badge.id())) continue;
            if (rule.metric() != Metric.TARGET && rule.metric() != Metric.CONTRACT_TARGET) break;
            var rows = new ArrayList<String>();
            for (var contract : Contract.values())
                rows.add(label.apply(contract) + " · " + AchievementCatalog.targetRule(contract, romanian));
            return String.join("\n", rows);
        }
        return "";
    }

    private static boolean isRentz(AchievementHand hand) {
        return hand.result().contract() == Contract.RENTZ;
    }

    private static int rank(AchievementHand hand) {
        var totals = hand.result().totals();
        return 1 + (int) totals.stream().filter(score -> score > totals.get(0)).count();
    }
}

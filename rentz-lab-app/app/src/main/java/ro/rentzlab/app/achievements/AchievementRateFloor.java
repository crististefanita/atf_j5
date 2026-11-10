package ro.rentzlab.app.achievements;

import java.util.EnumMap;
import java.util.Map;
import ro.rentzlab.app.achievements.AchievementCatalog.Level;
import ro.rentzlab.app.achievements.AchievementFullCatalog.Context;
import ro.rentzlab.app.achievements.AchievementTactics.Fact;
import ro.rentzlab.core.Contract;

/** Lower observed whole-percent rates from two disjoint legal-play batches. */
final class AchievementRateFloor {
    // Columns follow Context: any, 3, 4, 5, 6, 3–4, 5–6, Normal, Hard, own, opponent, open, blind.
    private static final String MEASURED = """
            TRICKS LOSE_FIRST 75 64 66 81 82 68 81 75 75 72 77 72 75
            TRICKS LOSE_LAST 74 63 79 73 79 71 77 74 74 73 74 73 74
            TRICKS AT_MOST_TWO 71 51 72 71 85 63 78 71 71 69 70 71 70
            TRICKS NO_RUN_OF_TWO 62 46 62 59 78 54 69 62 62 60 64 61 63
            TRICKS NO_TRICKS 19 11 13 19 26 12 22 19 19 17 21 20 19
            QUEENS QUEEN_PASSED 97 91 98 98 96 95 98 97 97 95 95 96 96
            QUEENS QUEEN_DISCARDED 20 25 23 19 13 24 16 20 20 20 18 18 20
            QUEENS NO_EARLY_QUEENS 62 41 61 70 64 52 67 62 62 57 66 62 59
            QUEENS AT_MOST_ONE_QUEEN 72 45 70 80 78 58 79 72 72 71 72 68 72
            QUEENS NO_QUEENS 41 20 37 52 54 29 53 41 41 41 41 40 42
            DIAMONDS FIRST_DIAMOND_PASSED 75 65 71 79 76 69 80 75 75 73 76 74 73
            DIAMONDS DIAMOND_DISCARDED 38 38 34 37 35 36 39 38 38 37 35 38 37
            DIAMONDS NO_EARLY_DIAMONDS 68 61 64 70 73 65 72 68 68 66 70 68 69
            DIAMONDS AT_MOST_TWO_DIAMONDS 64 52 61 66 66 58 67 64 64 61 63 65 63
            DIAMONDS NO_DIAMONDS 45 36 38 45 51 37 50 45 45 44 43 43 45
            KING_HEARTS KING_PASSED 76 62 69 86 80 69 83 76 76 75 78 77 76
            KING_HEARTS KING_FOLLOWED 34 20 30 36 38 25 37 34 34 26 38 31 35
            KING_HEARTS KING_DISCARDED 5 8 5 4 1 8 2 5 5 4 6 4 6
            KING_HEARTS KING_LEAD_PASSED 13 10 11 9 15 10 12 13 13 18 7 14 11
            KING_HEARTS KING_PASSED_AFTER_WIN 34 39 29 34 28 34 31 34 34 34 30 36 32
            TOTALS LOSE_FIRST 73 61 64 73 84 63 80 73 73 68 76 72 73
            TOTALS NO_KING 74 63 70 73 87 68 80 74 74 75 74 77 72
            TOTALS NO_QUEENS 42 22 33 46 56 32 52 42 42 40 44 43 40
            TOTALS NO_DIAMONDS 44 37 44 41 50 41 46 44 44 40 47 45 42
            TOTALS NO_TRICKS 19 7 12 20 34 9 27 19 19 15 22 20 17
            WHIST WIN_FIRST 20 25 22 17 12 23 17 20 20 20 20 19 22
            WHIST WIN_LAST 23 33 21 10 23 28 18 23 23 20 23 22 22
            WHIST RUN_OF_TWO 36 57 41 21 21 50 23 36 36 39 33 35 38
            WHIST AT_LEAST_THREE 27 46 30 18 12 39 15 27 27 32 21 27 27
            WHIST AT_LEAST_FOUR 14 31 17 3 5 24 5 14 14 17 11 14 15
            TEN_CLUBS TEN_CAPTURED 21 26 26 13 15 26 15 21 21 22 20 21 21
            TEN_CLUBS OPPONENT_TEN 18 23 23 12 12 24 13 18 18 18 18 18 19
            TEN_CLUBS OWN_TEN 1 1 1 1 0 1 0 1 1 1 0 1 1
            TEN_CLUBS TEN_ON_LEAD 10 9 6 5 5 11 6 10 10 11 8 9 8
            TEN_CLUBS TEN_AFTER_LOSS 17 23 19 11 11 21 12 17 17 19 15 17 17
            RENTZ OPEN 65 84 70 54 46 77 51 65 65 64 66 65 61
            RENTZ EXTEND_LOW 99 98 100 97 97 99 98 99 99 98 99 98 99
            RENTZ EXTEND_HIGH 97 95 96 97 96 97 97 97 97 96 97 97 96
            RENTZ ACE_RETAINS_TURN 51 70 50 48 36 60 42 51 51 53 50 52 50
            RENTZ TOP_TWO 45 67 40 36 32 54 34 45 45 45 40 47 42
            """;
    private static final Map<Contract, Map<Fact, int[]>> RATES = load();

    private AchievementRateFloor() { }

    static boolean allows(AchievementFullCatalog.ContractRule rule) {
        return percent(rule.contract(), rule.tactic().fact(), rule.context()) >= threshold(rule.level());
    }

    static int threshold(Level level) { return level == Level.HARD ? 16 : 12; }

    static int percent(Contract contract, Fact fact, Context context) {
        return RATES.get(contract).get(fact)[context.ordinal()];
    }

    private static Map<Contract, Map<Fact, int[]>> load() {
        Map<Contract, Map<Fact, int[]>> rates = new EnumMap<>(Contract.class);
        for (var line : MEASURED.split("\\n")) {
            var values = line.trim().split(" +");
            var byFact = rates.computeIfAbsent(Contract.valueOf(values[0]), ignored -> new EnumMap<>(Fact.class));
            if (values.length != Context.values().length + 2) throw new IllegalStateException("Invalid calibration row");
            var percentages = new int[Context.values().length];
            for (int i = 0; i < percentages.length; i++) percentages[i] = Integer.parseInt(values[i + 2]);
            if (byFact.put(Fact.valueOf(values[1]), percentages) != null) throw new IllegalStateException("Duplicate calibration row");
        }
        return rates;
    }
}

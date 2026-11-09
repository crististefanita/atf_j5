package ro.rentzlab.app.achievements;

import static ro.rentzlab.app.achievements.AchievementCatalog.*;
import static ro.rentzlab.app.achievements.AchievementTactics.Fact.*;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import ro.rentzlab.ai.RentzPlayer.Difficulty;
import ro.rentzlab.app.achievements.AchievementTactics.Fact;
import ro.rentzlab.core.Contract;

final class AchievementExpansion {
    record Tactic(Fact fact, String en, String ro) { }
    private AchievementExpansion() { }

    private static Tactic tactic(Fact fact, String en, String ro) { return new Tactic(fact, en, ro); }

    static List<Tactic> tactics(Contract contract) {
        return switch (contract) {
            case TRICKS -> List.of(
                    tactic(LOSE_FIRST, "Let someone else win the first trick", "Lasă prima levată unui adversar"),
                    tactic(LOSE_LAST, "Let someone else win the last trick", "Lasă ultima levată unui adversar"),
                    tactic(AT_MOST_TWO, "Take at most two tricks", "Ia cel mult două levate"),
                    tactic(NO_RUN_OF_TWO, "Avoid taking consecutive tricks", "Evită să iei levate consecutive"),
                    tactic(NO_TRICKS, "Finish without a trick", "Încheie fără nicio levată"));
            case QUEENS -> List.of(
                    tactic(QUEEN_PASSED, "Let a queen go to an opponent", "Lasă o damă să ajungă la un adversar"),
                    tactic(QUEEN_DISCARDED, "Discard a queen off suit", "Aruncă o damă în afara culorii"),
                    tactic(NO_EARLY_QUEENS, "Avoid queens in the first four tricks", "Evită damele în primele patru levate"),
                    tactic(AT_MOST_ONE_QUEEN, "Take at most one queen", "Ia cel mult o damă"),
                    tactic(NO_QUEENS, "Finish without a queen", "Încheie fără dame"));
            case DIAMONDS -> List.of(
                    tactic(FIRST_DIAMOND_PASSED, "Let the first diamond trick go", "Lasă prima levată cu caro unui adversar"),
                    tactic(DIAMOND_DISCARDED, "Discard a diamond off suit", "Aruncă un caro în afara culorii"),
                    tactic(NO_EARLY_DIAMONDS, "Avoid diamonds in the first four tricks", "Evită carourile în primele patru levate"),
                    tactic(AT_MOST_TWO_DIAMONDS, "Take at most two diamonds", "Ia cel mult două carouri"),
                    tactic(NO_DIAMONDS, "Finish without a diamond", "Încheie fără carouri"));
            case KING_HEARTS -> List.of(
                    tactic(KING_PASSED, "Let the king of hearts go", "Lasă regele de cupă unui adversar"),
                    tactic(KING_FOLLOWED, "Follow suit and let the king go", "Urmează culoarea și evită regele de cupă"),
                    tactic(KING_DISCARDED, "Discard the king off suit", "Aruncă regele de cupă în afara culorii"),
                    tactic(KING_LEAD_PASSED, "Lead the king's trick and let it go", "Deschide levata regelui și las-o unui adversar"),
                    tactic(KING_PASSED_AFTER_WIN, "Avoid the king after winning a trick", "Evită regele după ce ai luat o levată"));
            case TOTALS -> List.of(
                    tactic(LOSE_FIRST, "Let someone else win the first trick", "Lasă prima levată unui adversar"),
                    tactic(NO_KING, "Finish without the king of hearts", "Încheie fără regele de cupă"),
                    tactic(NO_QUEENS, "Finish without a queen", "Încheie fără dame"),
                    tactic(NO_DIAMONDS, "Finish without a diamond", "Încheie fără carouri"),
                    tactic(NO_TRICKS, "Finish without any penalty", "Încheie fără nicio penalizare"));
            case WHIST -> List.of(
                    tactic(WIN_FIRST, "Win the first trick", "Ia prima levată"),
                    tactic(WIN_LAST, "Win the last trick", "Ia ultima levată"),
                    tactic(RUN_OF_TWO, "Win two consecutive tricks", "Ia două levate consecutive"),
                    tactic(AT_LEAST_THREE, "Win at least three tricks", "Ia cel puțin trei levate"),
                    tactic(AT_LEAST_FOUR, "Win at least four tricks", "Ia cel puțin patru levate"));
            case TEN_CLUBS -> List.of(
                    tactic(TEN_CAPTURED, "Capture the ten of clubs", "Capturează zecele de treflă"),
                    tactic(OPPONENT_TEN, "Capture an opponent's ten", "Capturează zecele unui adversar"),
                    tactic(OWN_TEN, "Capture your own ten", "Capturează propriul zece de treflă"),
                    tactic(TEN_ON_LEAD, "Lead the trick that captures the ten", "Deschide levata care capturează zecele"),
                    tactic(TEN_AFTER_LOSS, "Capture the ten after losing a trick", "Capturează zecele după o levată pierdută"));
            case RENTZ -> List.of(
                    tactic(OPEN, "Open a suit at the starting rank", "Deschide o culoare la rangul de start"),
                    tactic(EXTEND_LOW, "Extend the lower end of a suit", "Extinde capătul inferior al unei culori"),
                    tactic(EXTEND_HIGH, "Extend the upper end of a suit", "Extinde capătul superior al unei culori"),
                    tactic(ACE_RETAINS_TURN, "Keep the turn after playing an ace", "Păstrează tura după ce joci un as"),
                    tactic(TOP_TWO, "Finish in the top two", "Termină printre primii doi"));
        };
    }

    static void easy(List<Badge> rows, Contract contract, int contractIndex) {
        var tactics = tactics(contract);
        for (int i = 0; i < tactics.size(); i++) {
            var tactic = tactics.get(i);
            int seats = 3 + (i + contractIndex) % 4;
            if (coveredByPilot(contract, tactic.fact())) {
                add(rows, contract, tactic, "once", Stage.CONTRACTS, Level.EASY, Unit.HAND, 1,
                        " · Your choice", " · Alegerea ta", "Choose the contract yourself.", "Alege personal contractul.",
                        AchievementHand::manualChoice);
            } else {
                add(rows, contract, tactic, "once", Stage.CONTRACTS, Level.EASY, Unit.HAND, 1,
                        "", "", "", "", h -> true);
            }
            boolean sameTargetAsPilot = contract != Contract.RENTZ && coveredByPilot(contract, tactic.fact());
            add(rows, contract, tactic, "twice", Stage.CONTRACTS, Level.EASY, Unit.HAND, 2,
                    sameTargetAsPilot ? " · Twice on your choice" : " · Twice",
                    sameTargetAsPilot ? " · De două ori la alegerea ta" : " · De două ori",
                    sameTargetAsPilot ? "Choose the contract yourself in two distinct hands." : "Use two distinct hands.",
                    sameTargetAsPilot ? "Alege personal contractul în două mâini distincte." : "Folosește două mâini distincte.",
                    sameTargetAsPilot ? AchievementHand::manualChoice : h -> true);
            add(rows, contract, tactic, "table-" + seats, Stage.CONTRACTS, Level.EASY, Unit.HAND, 1,
                    " · " + seats + " players", " · " + seats + " jucători",
                    "Play at a table with " + seats + " players.", "Joacă la o masă cu " + seats + " jucători.",
                    h -> h.players() == seats);
        }
    }

    static boolean coveredByPilot(Contract contract, Fact fact) {
        return switch (contract) {
            case QUEENS -> fact == NO_QUEENS;
            case DIAMONDS -> fact == NO_DIAMONDS;
            case KING_HEARTS -> fact == KING_PASSED;
            case WHIST -> fact == AT_LEAST_THREE;
            case TEN_CLUBS -> fact == TEN_CAPTURED;
            case RENTZ -> fact == OPEN;
            case TRICKS, TOTALS -> false;
        };
    }

    static void medium(List<Badge> rows, Contract contract) {
        var tactics = tactics(contract);
        var primary = tactics.get(primaryIndex(contract));
        medium(rows, contract, primary, "chosen", 3, "Your choice", "Alegerea ta",
                "Choose the contract yourself.", "Alege personal contractul.", AchievementHand::manualChoice);
        medium(rows, contract, primary, "opponent", 4, "Opponent's choice", "Alegerea unui adversar",
                "An opponent must choose the contract.", "Contractul trebuie ales de un adversar.", h -> h.result().chooser() != 0);
        medium(rows, contract, primary, "large", 4, "Large table", "Masă mare",
                "Play with five or six players.", "Joacă la o masă cu cinci sau șase jucători.", h -> h.players() >= 5);
        medium(rows, contract, primary, "hard", 3, "Hard opponents", "Adversari Greu",
                "Play against Hard opponents.", "Joacă împotriva adversarilor Greu.", h -> h.difficulty() == Difficulty.HARD);
        medium(rows, contract, tactics.get(2), "normal", 3, "Normal opponents", "Adversari Normal",
                "Play against Normal opponents.", "Joacă împotriva adversarilor Normal.", h -> h.difficulty() == Difficulty.NORMAL);
        medium(rows, contract, tactics.get(4), "blind", 3, "Blind contract", "Contract pe nevăzute",
                "The contract must be chosen blind.", "Contractul trebuie ales pe nevăzute.", h -> h.result().blind());
    }

    static int primaryIndex(Contract contract) {
        return switch (contract) { case KING_HEARTS, TEN_CLUBS -> 1; default -> 0; };
    }

    private static void medium(List<Badge> rows, Contract contract, Tactic tactic, String key, int goal,
            String titleEn, String titleRo, String ruleEn, String ruleRo, Predicate<AchievementHand> context) {
        add(rows, contract, tactic, key, Stage.TACTICS, Level.MEDIUM, Unit.HAND, goal, " · " + titleEn, " · " + titleRo,
                ruleEn, ruleRo, context);
    }

    static void hard(List<Badge> rows, Contract contract) {
        var tactic = tactics(contract).get(0);
        add(rows, contract, tactic, "days", Stage.MASTERY, Level.HARD, Unit.DAY, 3,
                " · Three days", " · Trei zile", "Use three different days and matches.",
                "Folosește trei zile și partide distincte.", h -> true);
        add(rows, contract, tactic, "hard-days", Stage.MASTERY, Level.HARD, Unit.DAY, 4,
                " · Four days on Hard", " · Patru zile pe Greu", "Use four different days and matches against Hard opponents.",
                "Folosește patru zile și partide distincte împotriva adversarilor Greu.", h -> h.difficulty() == Difficulty.HARD);
    }

    private static void add(List<Badge> rows, Contract contract, Tactic tactic, String key, Stage stage, Level level,
            Unit unit, int goal, String titleEn, String titleRo, String ruleEn, String ruleRo,
            Predicate<AchievementHand> context) {
        if (level == Level.EASY && tactic.fact() == OWN_TEN) {
            level = Level.HARD;
            stage = Stage.MASTERY;
        } else if (level == Level.EASY && tactic.fact() == KING_DISCARDED) {
            level = Level.MEDIUM;
            stage = Stage.TACTICS;
        }
        String id = "s2-" + contract.name().toLowerCase(Locale.ROOT) + "-" + tactic.fact().name().toLowerCase(Locale.ROOT) + "-" + key;
        rows.add(new Badge(id, stage, level, contract, unit, goal, tactic.en() + titleEn, tactic.ro() + titleRo,
                (tactic.en() + ". " + ruleEn).trim(), (tactic.ro() + ". " + ruleRo).trim(), PlayMode.MANUAL,
                context, hand -> hand.tactics().has(tactic.fact())));
    }
}

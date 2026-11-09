package ro.rentzlab.app.achievements;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.Contract;

public final class AchievementCatalog {
    public enum Stage { LEARN, CONTRACTS, TACTICS, MASTERY }
    public enum Level { EASY, MEDIUM, HARD }
    public enum Unit { HAND, CONTRACT, MATCH, DAY, TABLE }
    public enum PlayMode { MANUAL, MANUAL_MATCH, ASSISTED, EITHER }
    public enum Eligibility { NO_HAND, OTHER_CONTRACT, MATCH_IN_PROGRESS, NEED_MANUAL, NEED_ASSISTANCE, CONTEXT, TACTIC, ELIGIBLE }
    public record Badge(String id, Stage stage, Level level, Contract contract, Unit unit, int goal,
            String english, String romanian, String ruleEn, String ruleRo, PlayMode mode,
            Predicate<AchievementHand> context, Predicate<AchievementHand> qualifies) {
        public Eligibility eligibility(AchievementHand hand) {
            if (hand == null) return Eligibility.NO_HAND;
            if (contract != null && contract != hand.result().contract()) return Eligibility.OTHER_CONTRACT;
            if (mode == PlayMode.MANUAL && !hand.manual()) return Eligibility.NEED_MANUAL;
            if (mode == PlayMode.MANUAL_MATCH) {
                if (!hand.complete()) return Eligibility.MATCH_IN_PROGRESS;
                if (!hand.manualMatch()) return Eligibility.NEED_MANUAL;
            }
            if (mode == PlayMode.ASSISTED && !hand.delegated()) return Eligibility.NEED_ASSISTANCE;
            if (!context.test(hand)) return Eligibility.CONTEXT;
            return qualifies.test(hand) ? Eligibility.ELIGIBLE : Eligibility.TACTIC;
        }
        public boolean accepts(AchievementHand hand) { return eligibility(hand) == Eligibility.ELIGIBLE; }
    }

    private static final List<Contract> ORDER = List.of(Contract.TRICKS, Contract.QUEENS, Contract.DIAMONDS,
            Contract.KING_HEARTS, Contract.TOTALS, Contract.WHIST, Contract.TEN_CLUBS, Contract.RENTZ);
    private static final List<Badge> BADGES = create();
    private AchievementCatalog() { }
    public static List<Badge> badges() { return BADGES; }
    public static List<Badge> all() { return Combined.BADGES; }

    private static final class Combined {
        private static final List<Badge> BADGES = combine();

        private static List<Badge> combine() {
            var result = new ArrayList<>(AchievementCatalog.BADGES);
            result.addAll(GameplayAchievementCatalog.badges());
            return List.copyOf(result);
        }
    }

    private static List<Badge> create() {
        var rows = new ArrayList<Badge>();
        add(rows, "first-choice", Stage.LEARN, Level.EASY, null, Unit.HAND, 1, "First choice", "Prima alegere",
                "Finish a hand whose contract you chose yourself.", "Încheie o mână al cărei contract l-ai ales personal.", AchievementHand::manualChoice);
        add(rows, "first-card", Stage.LEARN, Level.EASY, null, Unit.HAND, 1, "First card", "Prima carte",
                "Play a card yourself in a hand that reaches its result.", "Joacă personal o carte într-o mână care ajunge la rezultat.", AchievementHand::manualPlay);
        add(rows, "first-hand", Stage.LEARN, Level.EASY, null, Unit.HAND, 1, "First hand", "Prima mână",
                "Finish a hand you took part in, with or without assistance.", "Încheie o mână la care ai participat, cu sau fără asistență.", AchievementHand::participated);
        add(rows, "assisted-hand", Stage.LEARN, Level.EASY, null, Unit.HAND, 1, "A hand with assistance", "O mână cu asistență",
                "Finish a hand in which Auto play made a decision for you.", "Încheie o mână în care jocul automat a decis pentru tine.", AchievementHand::delegated);
        for (int i = 0; i < ORDER.size(); i++) {
            var contract = ORDER.get(i);
            String id = contract.name().toLowerCase(java.util.Locale.ROOT);
            add(rows, id + "-intro", Stage.CONTRACTS, Level.EASY, contract, Unit.HAND, 1,
                    "First hand", "Prima mână", "Finish this contract, with or without assistance.",
                    "Încheie acest contract, cu sau fără asistență.", AchievementHand::participated);
            add(rows, id + "-manual", Stage.CONTRACTS, Level.EASY, contract, Unit.HAND, 1,
                    "At the controls", "La cârmă", "Finish this contract with manual play.", "Încheie acest contract prin joc manual.", AchievementHand::manual);
            add(rows, id + "-target", Stage.CONTRACTS, Level.EASY, contract, Unit.HAND, 1,
                    "Target met", "Țintă atinsă", targetRule(contract, false), targetRule(contract, true), AchievementCatalog::target);
            add(rows, id + "-repeat", Stage.CONTRACTS, Level.EASY, contract, Unit.HAND, 2,
                    "Target met twice", "Țintă atinsă de două ori", targetRule(contract, false), targetRule(contract, true), AchievementCatalog::target);
            AchievementExpansion.easy(rows, contract, i);
            AchievementFullCatalog.contract(rows, contract, Level.EASY);
        }
        AchievementFullCatalog.crossContract(rows, Level.EASY);
        add(rows, "three-contracts", Stage.TACTICS, Level.EASY, null, Unit.CONTRACT, 3, "Three contracts", "Trei contracte",
                "Finish three different contracts, with or without assistance.", "Încheie trei contracte diferite, cu sau fără asistență.", AchievementHand::participated);
        add(rows, "rentz-open", Stage.TACTICS, Level.EASY, Contract.RENTZ, Unit.HAND, 1, "Open a suit", "Deschide o culoare",
                "Open a suit in a manual Rentz hand.", "Deschide o culoare într-o mână Rentz jucată manual.", h -> h.manual() && h.opened());
        add(rows, "rentz-extend", Stage.TACTICS, Level.EASY, Contract.RENTZ, Unit.HAND, 1, "Extend a sequence", "Extinde un șir",
                "Extend a sequence in a manual hand whose Rentz contract you chose.",
                "Extinde un șir într-o mână Rentz aleasă personal și jucată manual.",
                h -> h.manualChoice() && h.extended());
        add(rows, "blind-choice", Stage.TACTICS, Level.EASY, null, Unit.HAND, 1, "A blind choice", "Alegere pe nevăzute",
                "Choose blind yourself and finish with manual play.", "Alege personal pe nevăzute și încheie prin joc manual.", h -> h.manual() && h.manualChoice() && h.result().blind());
        for (var contract : ORDER) {
            add(rows, contract.name().toLowerCase(java.util.Locale.ROOT) + "-chosen-target", Stage.TACTICS, Level.MEDIUM,
                    contract, Unit.HAND, 3, "Make your choice count", "Alegere cu rezultat",
                    "Choose this contract yourself and meet its target. " + targetRule(contract, false),
                    "Alege personal acest contract și atinge ținta. " + targetRule(contract, true),
                    h -> h.manualChoice() && target(h));
            AchievementExpansion.medium(rows, contract);
            AchievementFullCatalog.contract(rows, contract, Level.MEDIUM);
        }
        add(rows, "five-contracts", Stage.TACTICS, Level.MEDIUM, null, Unit.CONTRACT, 5, "Five contracts by hand", "Cinci contracte manual",
                "Finish five different contracts with manual play.", "Încheie cinci contracte diferite prin joc manual.", AchievementHand::manual);
        add(rows, "hard-targets", Stage.TACTICS, Level.MEDIUM, null, Unit.HAND, 3, "Targets on Hard", "Ținte pe Greu",
                "Meet manual contract targets against Hard opponents.", "Atinge ținte manuale împotriva adversarilor Greu.", h -> hard(h) && target(h));
        add(rows, "blind-targets", Stage.TACTICS, Level.MEDIUM, null, Unit.HAND, 2, "Blind but prepared", "Pregătit pe nevăzute",
                "Choose blind yourself and meet the manual contract target.", "Alege personal pe nevăzute și atinge ținta manuală.", h -> h.manualChoice() && h.result().blind() && target(h));
        add(rows, "complete-match", Stage.TACTICS, Level.MEDIUM, null, Unit.MATCH, 1, "To the final table", "Până la clasament",
                "Reach the final standings and take part in the final hand.", "Ajungi la clasamentul final și participi la ultima mână.", h -> h.complete() && h.participated());
        AchievementFullCatalog.crossContract(rows, Level.MEDIUM);
        add(rows, "active-days", Stage.MASTERY, Level.HARD, null, Unit.DAY, 3, "Three days at the table", "Trei zile la masă",
                "Finish a manual hand per day, in different matches.", "Încheie câte o mână manuală pe zi, în partide diferite.", AchievementHand::manual);
        add(rows, "target-days", Stage.MASTERY, Level.HARD, null, Unit.DAY, 4, "Four days on target", "Patru zile la țintă",
                "Meet a manual contract target per day, in different matches.", "Atinge câte o țintă manuală pe zi, în partide diferite.", AchievementCatalog::target);
        add(rows, "hard-days", Stage.MASTERY, Level.HARD, null, Unit.DAY, 4, "Four days on Hard", "Patru zile pe Greu",
                "Meet a manual target per day on Hard, in different matches.", "Atinge câte o țintă manuală pe zi pe Greu, în partide diferite.", h -> hard(h) && target(h));
        add(rows, "rentz-days", Stage.MASTERY, Level.HARD, Contract.RENTZ, Unit.DAY, 3, "Rentz first on three days", "Primul la Rentz în trei zile",
                "Finish first manually on three days, in different matches.", "Termină primul prin joc manual în trei zile, în partide diferite.", AchievementCatalog::target);
        for (var contract : ORDER) {
            AchievementExpansion.hard(rows, contract);
            AchievementFullCatalog.contract(rows, contract, Level.HARD);
        }
        AchievementFullCatalog.crossContract(rows, Level.HARD);
        rows.sort(java.util.Comparator.comparing(Badge::stage));
        return List.copyOf(rows);
    }

    private static void add(List<Badge> rows, String id, Stage stage, Level level, Contract contract, Unit unit,
            int goal, String en, String ro, String ruleEn, String ruleRo, Predicate<AchievementHand> condition) {
        var mode = switch (id) {
            case "assisted-hand" -> PlayMode.ASSISTED;
            case "first-choice", "first-card", "first-hand", "three-contracts", "complete-match" -> PlayMode.EITHER;
            default -> id.endsWith("-intro") ? PlayMode.EITHER : PlayMode.MANUAL;
        };
        rows.add(new Badge(id, stage, level, contract, unit, goal, en, ro, ruleEn, ruleRo, mode, h -> true, condition));
    }

    private static boolean hard(AchievementHand h) { return h.difficulty() == RentzPlayer.Difficulty.HARD; }

    static boolean target(AchievementHand h) {
        if (!h.manual()) return false;
        var scoring = h.result().scoring();
        if (scoring.bySeat().isEmpty()) return false;
        var contributions = scoring.bySeat().get(0);
        return switch (h.result().contract()) {
            case TRICKS -> contributions.get(0).count() <= 1;
            case QUEENS, DIAMONDS, KING_HEARTS -> contributions.get(0).count() == 0;
            case TOTALS -> contributions.stream().allMatch(c -> c.count() <= (c.category() == Contract.TRICKS ? 2 : 0));
            case WHIST -> contributions.get(0).count() >= 3;
            case TEN_CLUBS -> contributions.get(0).count() == 1;
            case RENTZ -> scoring.finishOrder().get(0) == 0;
        };
    }

    static String targetRule(Contract contract, boolean ro) {
        return switch (contract) {
            case TRICKS -> ro ? "Ia cel mult o levată prin joc manual." : "Take at most one trick with manual play.";
            case QUEENS -> ro ? "Evită toate damele prin joc manual." : "Avoid every queen with manual play.";
            case DIAMONDS -> ro ? "Evită toate carourile prin joc manual." : "Avoid every diamond with manual play.";
            case KING_HEARTS -> ro ? "Evită K♥ prin joc manual." : "Avoid K♥ with manual play.";
            case TOTALS -> ro ? "Ia cel mult două levate și nicio damă, carte de caro sau K♥." : "Take at most two tricks and no queen, diamond or K♥.";
            case WHIST -> ro ? "Ia cel puțin trei levate prin joc manual." : "Take at least three tricks with manual play.";
            case TEN_CLUBS -> ro ? "Capturează 10♣ prin joc manual." : "Capture 10♣ with manual play.";
            case RENTZ -> ro ? "Termină primul prin joc manual." : "Finish first with manual play.";
        };
    }
}

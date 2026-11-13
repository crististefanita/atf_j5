package ro.rentzlab.app;

import ro.rentzlab.ai.*;

import ro.cardtable.names.PlayerNames;
import ro.rentzlab.app.achievements.AchievementHand;
import ro.rentzlab.core.*;
import ro.rentzlab.core.sequence.SequenceOpening;
import java.util.*;

/** Transport/lifecycle boundary. The dealing seed and journal never enter a player decision. */
public final class GameSession {
    public String scoreProfileId() { return dealEnding.laboratoryProfileId(); }
    private static final ScoreProfile SCORING = ScoreProfile.laboratory();
    public enum Action { REVEAL, CHOOSE, PLAY, PASS, NEXT_HAND }
    public enum Origin { UNKNOWN, MANUAL, DELEGATED, AUTOMATIC }
    public record Command(Action action,int seat,Card card,Contract contract,boolean blind,Origin origin) {
        public Command(Action action,int seat,Card card,Contract contract,boolean blind) {
            this(action,seat,card,contract,blind,Origin.UNKNOWN);
        }
        public Command {
            Objects.requireNonNull(origin);
            if (((origin == Origin.MANUAL || origin == Origin.DELEGATED) && seat != 0)
                    || (origin == Origin.AUTOMATIC && seat == 0 && action != Action.PASS)) {
                throw new IllegalArgumentException("Command origin differs from actor");
            }
        }
        public Command from(Origin source) { return new Command(action, seat, card, contract, blind, source); }
    }
    public record Saved(int schema,int players,long seed,RentzPlayer.Difficulty difficulty,List<Command> commands,List<String> opponents,
                        RentzMatch.DealEnding dealEnding, SequenceOpening sequenceOpening) {
        public Saved(int schema,int players,long seed,RentzPlayer.Difficulty difficulty,List<Command> commands) {
            this(schema,players,seed,difficulty,commands,List.of(),RentzMatch.DealEnding.UNIQUE_LAST,SequenceOpening.ACTIVE_MIDDLE);
        }
        public Saved(int schema,int players,long seed,RentzPlayer.Difficulty difficulty,List<Command> commands,List<String> opponents) {
            this(schema,players,seed,difficulty,commands,opponents,RentzMatch.DealEnding.UNIQUE_LAST,SequenceOpening.ACTIVE_MIDDLE);
        }
        public Saved(int schema,int players,long seed,RentzPlayer.Difficulty difficulty,List<Command> commands,List<String> opponents,
                     RentzMatch.DealEnding dealEnding) {
            this(schema,players,seed,difficulty,commands,opponents,dealEnding,SequenceOpening.ACTIVE_MIDDLE);
        }
        public Saved {
            commands=Card.immutable(commands);
            opponents=Card.immutable(opponents);
            Objects.requireNonNull(dealEnding);
            Objects.requireNonNull(sequenceOpening);
            if(schema!=1)throw new IllegalArgumentException("Saved session schema");
        }
    }
    private final int players;
    private final long seed;
    private final RentzPlayer.Difficulty difficulty;
    private final RentzMatch match;
    private final RentzMatch.DealEnding dealEnding;
    private final SequenceOpening sequenceOpening;
    private final RentzPlayer player;
    private final List<String> opponents;
    private final List<Command> commands=new ArrayList<>();
    private final AchievementHand.Recorder achievementRecorder;
    private final List<AchievementHand> achievementHands = new ArrayList<>();
    public GameSession(int players,long seed,RentzPlayer.Difficulty difficulty) {
        this(players,seed,difficulty,List.of(),RentzMatch.DealEnding.UNIQUE_LAST,SequenceOpening.ACTIVE_MIDDLE);
    }
    public GameSession(int players,long seed,RentzPlayer.Difficulty difficulty,String rosterName) {
        this(players,seed,difficulty,PlayerNames.forMatch(players,rosterName,seed).subList(1,players),
                RentzMatch.DealEnding.UNIQUE_LAST,SequenceOpening.ACTIVE_MIDDLE);
    }
    private GameSession(int players,long seed,RentzPlayer.Difficulty difficulty,List<String> savedOpponents,
                        RentzMatch.DealEnding dealEnding, SequenceOpening sequenceOpening) {
        this.players=players; this.seed=seed; this.difficulty=Objects.requireNonNull(difficulty);
        if (!savedOpponents.isEmpty() && (savedOpponents.size()!=players-1
                || savedOpponents.stream().map(name->name.toLowerCase(Locale.ROOT)).distinct().count()!=savedOpponents.size()
                || savedOpponents.stream().anyMatch(name->!PlayerNames.normalize(name).equals(name))))
            throw new IllegalArgumentException("Saved opponents");
        opponents=savedOpponents.isEmpty()?defaultOpponents(players,seed):Card.immutable(savedOpponents);
        this.dealEnding=dealEnding;
        this.sequenceOpening=Objects.requireNonNull(sequenceOpening);
        var scoring = dealEnding.laboratoryScoring();
        match=new RentzMatch(players,seed,scoring,0,dealEnding,sequenceOpening);
        achievementRecorder = new AchievementHand.Recorder(players,sequenceOpening);
        player=new RentzPlayer(difficulty,scoring,PolicySettings.defaults());
    }
    public static ScoreProfile scoringProfile(){return SCORING;}
    public static GameSession restore(Saved saved){
        var session=new GameSession(saved.players(),saved.seed(),saved.difficulty(),saved.opponents(),saved.dealEnding(),saved.sequenceOpening());
        for(var command:saved.commands())session.apply(command);
        return session;
    }
    public void apply(Command command){
        switch(command.action()){
            case REVEAL->match.reveal(command.seat());case CHOOSE->match.choose(command.seat(),command.contract(),command.blind());
            case PLAY->match.play(command.seat(),command.card());case PASS->match.pass(command.seat());case NEXT_HAND->match.nextHand();
        }
        commands.add(command);
        achievementRecorder.observe(command);
        if (command.action() == Action.PLAY || command.action() == Action.PASS) {
            var history = match.history();
            if (history.size() > achievementHands.size()) {
                achievementHands.add(achievementRecorder.settle(history.get(history.size() - 1), difficulty));
            }
        }
    }
    public void advanceBots(){
        int actions=0;
        while(hasPendingBotTurn()){
            if(++actions>512)throw new IllegalStateException("Bot action bound");
            advanceBotTurn();
        }
    }
    public void advanceBotTurn() {
        var state = view();
        if (isDecisionPhase(state) && state.turn() != state.seat()) advancePolicyTurn(state.turn());
    }
    public void advanceHumanTurn() {
        if (hasPendingHumanDecision()) advancePolicyTurn(0);
    }
    private void advancePolicyTurn(int actor) {
        var observation = match.observe(actor);
        var origin = actor == 0 ? Origin.DELEGATED : Origin.AUTOMATIC;
        if (observation.phase() == RentzMatch.Phase.CHOOSE) {
            if (!observation.revealed()) apply(new Command(Action.REVEAL, actor, null, null, false, origin));
            apply(new Command(Action.CHOOSE, actor, null, player.choose(match.observe(actor)).selected(), false, origin));
        } else if (observation.legal().isEmpty()) {
            apply(new Command(Action.PASS, actor, null, null, false, Origin.AUTOMATIC));
        } else {
            apply(new Command(Action.PLAY, actor, player.card(observation).selected(), null, false, origin));
        }
    }
    public boolean hasPendingBotTurn() {
        var state = view();
        return state.turn() != state.seat() && isDecisionPhase(state);
    }
    public boolean hasPendingHumanDecision() {
        var state = view();
        return state.turn() == state.seat() && isDecisionPhase(state)
                && (state.phase() == RentzMatch.Phase.CHOOSE || !state.legal().isEmpty());
    }
    private static boolean isDecisionPhase(RentzMatch.Observation state) {
        return state.phase() == RentzMatch.Phase.CHOOSE || state.phase() == RentzMatch.Phase.PLAY;
    }
    public boolean hasForcedHumanPass() {
        var state = view();
        return state.contract() == Contract.RENTZ && state.phase() == RentzMatch.Phase.PLAY
                && state.turn() == state.seat() && state.legal().isEmpty();
    }
    public RentzMatch.Observation view(){return match.observe(0);}
    public boolean blindChoiceAvailable(){var view=view();return view.phase()==RentzMatch.Phase.CHOOSE&&view.turn()==0&&!view.revealed()&&view.available().size()>1;}
    public List<RentzMatch.HandResult> history(){return match.history();}
    public List<AchievementHand> achievementHands() { return List.copyOf(achievementHands); }
    public AchievementHand latestAchievementHand() {
        return achievementHands.isEmpty() ? null : achievementHands.get(achievementHands.size() - 1);
    }
    public RentzPlayer.Difficulty difficulty(){return difficulty;}
    static List<String> defaultOpponents(int players,long seed) {
        return PlayerNames.forMatch(players,PlayerNames.DEFAULT_HUMAN,seed).subList(1,players);
    }
    public List<String> opponentNames() { return opponents; }
    public List<String> displayNames(String humanName) {
        var result = new ArrayList<String>(players);
        result.add(humanName);
        result.addAll(opponents);
        return Collections.unmodifiableList(result);
    }
    public Saved save(){return new Saved(1,players,seed,difficulty,commands,opponents,dealEnding,sequenceOpening);}
}

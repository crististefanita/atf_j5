package ro.rentzlab.app;

import ro.rentzlab.ai.*;

import ro.rentzlab.core.*;
import java.util.*;

/** Transport/lifecycle boundary. The dealing seed and journal never enter a player decision. */
public final class GameSession {
    public enum Action { REVEAL, CHOOSE, PLAY, PASS, NEXT_HAND }
    public record Command(Action action,int seat,Card card,Contract contract,boolean blind) { }
    public record Saved(int schema,int players,long seed,RentzPlayer.Difficulty difficulty,List<Command> commands) {
        public Saved {commands=Card.immutable(commands);if(schema!=1)throw new IllegalArgumentException("Saved session schema");}
    }
    private final int players;
    private final long seed;
    private final RentzPlayer.Difficulty difficulty;
    private final RentzMatch match;
    private final RentzPlayer player;
    private final List<Command> commands=new ArrayList<>();
    public GameSession(int players,long seed,RentzPlayer.Difficulty difficulty){this.players=players;this.seed=seed;this.difficulty=Objects.requireNonNull(difficulty);match=new RentzMatch(players,seed,ScoreProfile.laboratory());player=new RentzPlayer(difficulty,ScoreProfile.laboratory(),PolicySettings.defaults());}
    public static GameSession restore(Saved saved){var session=new GameSession(saved.players(),saved.seed(),saved.difficulty());for(var command:saved.commands())session.apply(command);return session;}
    public void apply(Command command){
        switch(command.action()){
            case REVEAL->match.reveal(command.seat());case CHOOSE->match.choose(command.seat(),command.contract(),command.blind());
            case PLAY->match.play(command.seat(),command.card());case PASS->match.pass(command.seat());case NEXT_HAND->match.nextHand();
        }
        commands.add(command);
    }
    public void advanceBots(){
        int actions=0;
        while(match.observe(0).turn()!=0&&(match.observe(0).phase()==RentzMatch.Phase.CHOOSE||match.observe(0).phase()==RentzMatch.Phase.PLAY)){
            if(++actions>512)throw new IllegalStateException("Bot action bound");int actor=match.observe(0).turn();
            if(match.observe(actor).phase()==RentzMatch.Phase.CHOOSE){apply(new Command(Action.REVEAL,actor,null,null,false));apply(new Command(Action.CHOOSE,actor,null,player.choose(match.observe(actor)).selected(),false));}
            else {var view=match.observe(actor);if(view.legal().isEmpty())apply(new Command(Action.PASS,actor,null,null,false));else apply(new Command(Action.PLAY,actor,player.card(view).selected(),null,false));}
        }
    }
    public RentzMatch.Observation view(){return match.observe(0);}
    public boolean blindChoiceAvailable(){var view=view();return view.phase()==RentzMatch.Phase.CHOOSE&&view.turn()==0&&!view.revealed()&&view.available().size()>1;}
    public List<RentzMatch.HandResult> history(){return match.history();}
    public RentzPlayer.Difficulty difficulty(){return difficulty;}
    public Saved save(){return new Saved(1,players,seed,difficulty,commands);}
}

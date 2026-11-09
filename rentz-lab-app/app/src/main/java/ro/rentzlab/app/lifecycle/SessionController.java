package ro.rentzlab.app.lifecycle;

import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.achievements.AchievementProgress;
import ro.rentzlab.ai.RentzPlayer;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

/** Owner checkpoints; background work only sees a detached replay of immutable input. */
public final class SessionController implements AutoCloseable {
    public interface Listener {void ready();void failed(Exception error);}
    @FunctionalInterface public interface Advance {void bots(GameSession session) throws Exception;}
    @FunctionalInterface private interface Preparation {GameSession prepare() throws Exception;}
    private final SessionRepository repository;
    private final ExecutorService worker;
    private final Executor delivery;
    private final Listener listener;
    private final Advance advance;
    private SessionRepository.Owner owner;
    private GameSession session;
    private FutureTask<Void> pending;
    private boolean pendingHumanAutomation;
    private long generation;
    private boolean active,closed;

    public SessionController(SessionRepository repository,ExecutorService worker,Executor delivery,Listener listener){
        this(repository,worker,delivery,listener,GameSession::advanceBotTurn);
    }
    public SessionController(SessionRepository repository,ExecutorService worker,Executor delivery,Listener listener,Advance advance){
        this.repository=Objects.requireNonNull(repository);this.worker=Objects.requireNonNull(worker);
        this.delivery=Objects.requireNonNull(delivery);this.listener=Objects.requireNonNull(listener);this.advance=Objects.requireNonNull(advance);
    }
    public synchronized boolean activate() throws Exception {
        if (closed) throw new IllegalStateException("Closed session controller");
        if (active && owner.isCurrent()) return false;
        deactivate();
        owner = null;
        session = null;

        var claimed = repository.claim();
        try {
            session = claimed.load();
        } catch (Exception error) {
            claimed.close();
            throw error;
        }
        owner = claimed;
        active = true;
        return true;
    }
    public synchronized void deactivate(){active=false;invalidate();if(owner!=null)owner.close();}
    private void invalidate(){generation++;if(pending!=null)pending.cancel(true);clearPending();}
    private void clearPending(){pending=null;pendingHumanAutomation=false;}
    public synchronized GameSession session(){return session;}
    public synchronized AchievementProgress achievements() { return active && owner.isCurrent() ? owner.progress() : null; }
    public synchronized boolean isBusy(){return pending!=null;}
    public synchronized boolean newGame(int players,long seed,RentzPlayer.Difficulty difficulty){
        return newGame(players,seed,difficulty,ro.cardtable.names.PlayerNames.DEFAULT_HUMAN);
    }
    public synchronized boolean newGame(int players,long seed,RentzPlayer.Difficulty difficulty,String rosterName){
        return checkpoint(()->new GameSession(players,seed,difficulty,rosterName));
    }
    public synchronized boolean apply(GameSession.Command command){
        return checkpoint(()->{var candidate=GameSession.restore(requireSession().save());candidate.apply(command);return candidate;});
    }
    private GameSession requireSession(){if(session==null)throw new IllegalStateException("No active game");return session;}
    private void requireActive(){
        if(closed||!active||!owner.isCurrent())throw new IllegalStateException("Inactive session owner");
    }
    private boolean checkpoint(Preparation preparation){
        requireActive();
        if(pending!=null)return false;
        try{
            var prepared=preparation.prepare();
            owner.commit(prepared);
            session=prepared;
            return true;
        }
        catch(Exception error){owner.runIfCurrent(()->listener.failed(error));return false;}
    }
    public synchronized boolean resume(){
        return submit(advance, false);
    }
    public synchronized boolean resumeHumanTurn(){
        requireActive();
        if (!requireSession().hasPendingHumanDecision()) return false;
        return submit(GameSession::advanceHumanTurn, true);
    }
    public synchronized void cancelHumanAutomation(){
        if (pendingHumanAutomation) invalidate();
    }
    private boolean submit(Advance turn, boolean humanAutomation){
        requireActive();
        if(pending!=null)return false;
        var origin=requireSession();var input=origin.save();long ticket=++generation;
        var task=new FutureTask<Void>(()->{
            try{var candidate=GameSession.restore(input);turn.bots(candidate);dispatch(ticket,origin,input,candidate,null);}
            catch(Exception error){dispatch(ticket,origin,input,null,error);}
            return null;
        });
        pending=task;
        pendingHumanAutomation=humanAutomation;
        try{worker.execute(task);return true;}
        catch(RuntimeException error){
            if(pending==task){clearPending();generation++;}
            task.cancel(true);owner.runIfCurrent(()->listener.failed(error));return false;
        }
    }
    private void dispatch(long ticket,GameSession origin,GameSession.Saved input,GameSession candidate,Exception failure){
        synchronized(this){if(closed||!active||ticket!=generation||pending==null)return;}
        try{delivery.execute(()->accept(ticket,origin,input,candidate,failure));}
        catch(RuntimeException rejected){synchronized(this){if(ticket==generation){clearPending();generation++;}}}
    }
    private synchronized void accept(long ticket,GameSession origin,GameSession.Saved input,GameSession candidate,Exception failure){
        if(closed||!active||ticket!=generation||pending==null)return;
        clearPending();
        if(session!=origin||!session.save().equals(input))return;
        owner.runIfCurrent(()->{
            if(failure!=null){listener.failed(failure);return;}
            try{if(!candidate.save().equals(input)){owner.commit(candidate);session=candidate;}}
            catch(Exception error){listener.failed(error);return;}
            listener.ready();
        });
    }
    public synchronized void publish(String screen) throws Exception {
        publish(screen, false);
    }
    public synchronized void publish(String screen, boolean autoPlay) throws Exception {
        if(active&&!closed&&session!=null){
            boolean waitingForAutomaticTurn=screen.equals("GAME")&&(session.hasPendingBotTurn()
                    ||session.hasForcedHumanPass()||(autoPlay&&session.hasPendingHumanDecision()));
            owner.publishIfCurrent(session,screen,isBusy()||waitingForAutomaticTurn);
        }
    }
    @Override public synchronized void close(){if(closed)return;deactivate();closed=true;worker.shutdownNow();}
}

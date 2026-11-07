package ro.rentzlab.app.lifecycle;

import ro.rentzlab.app.GameSession;
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
    private long generation;
    private boolean active,closed;

    public SessionController(SessionRepository repository,ExecutorService worker,Executor delivery,Listener listener){
        this(repository,worker,delivery,listener,GameSession::advanceBots);
    }
    public SessionController(SessionRepository repository,ExecutorService worker,Executor delivery,Listener listener,Advance advance){
        this.repository=Objects.requireNonNull(repository);this.worker=Objects.requireNonNull(worker);
        this.delivery=Objects.requireNonNull(delivery);this.listener=Objects.requireNonNull(listener);this.advance=Objects.requireNonNull(advance);
    }
    public synchronized boolean activate() throws Exception {
        if(closed)throw new IllegalStateException("Closed session controller");
        if(active&&owner.isCurrent())return false;
        invalidate();if(owner!=null)owner.close();
        owner=repository.claim();active=true;session=null;session=owner.load();return true;
    }
    public synchronized void deactivate(){active=false;invalidate();if(owner!=null)owner.close();}
    private void invalidate(){generation++;if(pending!=null)pending.cancel(true);pending=null;}
    public synchronized GameSession session(){return session;}
    public synchronized boolean isBusy(){return pending!=null;}
    public synchronized boolean newGame(int players,long seed,RentzPlayer.Difficulty difficulty){
        return newGame(players,seed,difficulty,ro.cardtable.names.PlayerNames.DEFAULT_HUMAN);
    }
    public synchronized boolean newGame(int players,long seed,RentzPlayer.Difficulty difficulty,String rosterName){
        return begin(()->new GameSession(players,seed,difficulty,rosterName),true);
    }
    public synchronized boolean apply(GameSession.Command command){
        return begin(()->{var candidate=GameSession.restore(requireSession().save());candidate.apply(command);return candidate;},true);
    }
    public synchronized boolean resume(){return begin(this::requireSession,false);}
    private GameSession requireSession(){if(session==null)throw new IllegalStateException("No active game");return session;}
    private boolean begin(Preparation preparation,boolean checkpoint){
        if(closed||!active||!owner.isCurrent())throw new IllegalStateException("Inactive session owner");
        if(pending!=null)return false;
        GameSession prepared;
        try{prepared=preparation.prepare();if(checkpoint)owner.commit(prepared);}
        catch(Exception error){owner.runIfCurrent(()->listener.failed(error));return false;}
        session=prepared;
        var input=prepared.save();var origin=prepared;long ticket=++generation;
        var task=new FutureTask<Void>(()->{
            try{var candidate=GameSession.restore(input);advance.bots(candidate);dispatch(ticket,origin,input,candidate,null);}
            catch(Exception error){dispatch(ticket,origin,input,null,error);}
            return null;
        });
        pending=task;
        try{worker.execute(task);return true;}
        catch(RuntimeException error){
            if(pending==task){pending=null;generation++;}
            task.cancel(true);owner.runIfCurrent(()->listener.failed(error));return false;
        }
    }
    private void dispatch(long ticket,GameSession origin,GameSession.Saved input,GameSession candidate,Exception failure){
        synchronized(this){if(closed||!active||ticket!=generation||pending==null)return;}
        try{delivery.execute(()->accept(ticket,origin,input,candidate,failure));}
        catch(RuntimeException rejected){synchronized(this){if(ticket==generation){pending=null;generation++;}}}
    }
    private synchronized void accept(long ticket,GameSession origin,GameSession.Saved input,GameSession candidate,Exception failure){
        if(closed||!active||ticket!=generation||pending==null)return;
        pending=null;
        if(session!=origin||!session.save().equals(input))return;
        owner.runIfCurrent(()->{
            if(failure!=null){listener.failed(failure);return;}
            try{if(!candidate.save().equals(input)){owner.commit(candidate);session=candidate;}}
            catch(Exception error){listener.failed(error);return;}
            listener.ready();
        });
    }
    public synchronized void publish(String screen) throws Exception {
        if(active&&!closed&&session!=null)owner.publishIfCurrent(session,screen,isBusy());
    }
    @Override public synchronized void close(){if(closed)return;deactivate();closed=true;worker.shutdownNow();}
}

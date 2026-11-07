package ro.rentzlab.app.lifecycle;

import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.SessionJson;
import ro.rentzlab.ai.RentzPlayer;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

final class LifecycleFixture implements AutoCloseable {
    static final GameSession.Command REVEAL=new GameSession.Command(GameSession.Action.REVEAL,0,null,null,false);
    final Memory storage=new Memory();
    final SessionRepository repository=new SessionRepository(storage);
    final Worker worker=new Worker();
    final Queue delivery=new Queue();
    final Events events=new Events();
    final SessionController controller;
    LifecycleFixture() throws Exception {this(s->{});}
    LifecycleFixture(SessionController.Advance advance) throws Exception {
        storage.saved=SessionJson.saved(new GameSession(4,31,RentzPlayer.Difficulty.NORMAL)).toString();
        controller=advance==null
                ? new SessionController(repository,worker,delivery,events)
                : new SessionController(repository,worker,delivery,events,advance);
        controller.activate();
    }
    @Override public void close(){controller.close();Thread.interrupted();}
    static final class Memory implements SessionStorage {
        String saved,actor;boolean fail,failRead;int writes;
        @Override public String read() throws IOException {
            if(failRead)throw new IOException("injected read failure");
            return saved;
        }
        @Override public void write(String name,String text) throws IOException {
            if(fail)throw new IOException("injected persistence failure");
            if(name.equals("session.json"))saved=text;else actor=text;writes++;
        }
        GameSession restored() throws Exception {return SessionJson.restore(saved);}
    }
    static final class Events implements SessionController.Listener {
        int ready,failed;Exception error;
        @Override public void ready(){ready++;}
        @Override public void failed(Exception error){failed++;this.error=error;}
    }
    static final class Queue implements Executor {
        final Deque<Runnable> tasks=new ArrayDeque<>();
        boolean reject;
        @Override public void execute(Runnable action){if(reject)throw new RejectedExecutionException("closed delivery");tasks.add(action);}
        void all(){int count=0;while(!tasks.isEmpty()){if(++count>100)throw new AssertionError("Delivery loop");tasks.remove().run();}}
    }
    static final class Worker extends AbstractExecutorService {
        final Deque<Runnable> tasks=new ArrayDeque<>();boolean closed;
        @Override public void execute(Runnable action){if(closed)throw new RejectedExecutionException("closed worker");tasks.add(action);}
        void next(){try{tasks.remove().run();}finally{Thread.interrupted();}}
        @Override public void shutdown(){closed=true;}
        @Override public List<Runnable> shutdownNow(){closed=true;var copy=List.copyOf(tasks);tasks.clear();return copy;}
        @Override public boolean isShutdown(){return closed;}
        @Override public boolean isTerminated(){return closed&&tasks.isEmpty();}
        @Override public boolean awaitTermination(long time,TimeUnit unit){return isTerminated();}
    }
}

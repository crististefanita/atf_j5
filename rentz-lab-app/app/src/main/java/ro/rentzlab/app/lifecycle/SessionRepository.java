package ro.rentzlab.app.lifecycle;

import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.SessionJson;
import java.util.Objects;

/** App-lifetime persistence boundary shared by Activity instances. */
public final class SessionRepository {
    private final SessionStorage storage;
    private long generation;
    public SessionRepository(SessionStorage storage){this.storage=Objects.requireNonNull(storage);}
    public synchronized Owner claim(){return new Owner(++generation);}
    public final class Owner implements AutoCloseable {
        private final long ticket;
        private boolean retired;
        private Owner(long ticket){this.ticket=ticket;}
        public boolean isCurrent(){synchronized(SessionRepository.this){return current();}}
        private boolean current(){return !retired&&ticket==generation;}
        private void requireCurrent(){if(!current())throw new IllegalStateException("Obsolete session owner");}
        public GameSession load() throws Exception {
            synchronized(SessionRepository.this){requireCurrent();String saved=storage.read();return saved==null?null:SessionJson.restore(saved);}
        }
        public void commit(GameSession session) throws Exception {
            synchronized(SessionRepository.this){requireCurrent();storage.write("session.json",SessionJson.saved(session).toString());}
        }
        public void publish(GameSession session,String screen,boolean busy) throws Exception {
            synchronized(SessionRepository.this){requireCurrent();storage.write("actor-view.json",SessionJson.actor(session,screen,busy).toString());}
        }
        public boolean publishIfCurrent(GameSession session,String screen,boolean busy) throws Exception {
            synchronized(SessionRepository.this){if(!current())return false;publish(session,screen,busy);return true;}
        }
        public boolean runIfCurrent(Runnable action){
            synchronized(SessionRepository.this){if(!current())return false;action.run();return true;}
        }
        @Override public void close(){
            synchronized(SessionRepository.this){if(current())generation++;retired=true;}
        }
    }
}

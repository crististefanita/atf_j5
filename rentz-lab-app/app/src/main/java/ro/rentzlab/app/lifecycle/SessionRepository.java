package ro.rentzlab.app.lifecycle;

import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.SessionJson;
import ro.rentzlab.app.achievements.AchievementProgress;
import ro.rentzlab.app.profile.LocalProfile;
import java.time.Clock;
import java.util.Objects;

/** App-lifetime persistence boundary shared by Activity instances. */
public final class SessionRepository {
    private final SessionStorage storage;
    private final LocalProfile profile;
    private final Clock clock;
    private long generation;
    public SessionRepository(SessionStorage storage){this(storage, null, Clock.systemUTC());}
    public SessionRepository(SessionStorage storage, LocalProfile profile, Clock clock) {
        this.storage = Objects.requireNonNull(storage);
        this.profile = profile;
        this.clock = Objects.requireNonNull(clock);
    }
    public synchronized Owner claim(){return new Owner(++generation);}
    public final class Owner implements AutoCloseable {
        private final long ticket;
        private boolean retired;
        private boolean loaded;
        private GameSession.Saved durable;
        private int durableHandCount;
        private AchievementProgress progress;
        private Owner(long ticket){this.ticket=ticket;}
        public boolean isCurrent(){synchronized(SessionRepository.this){return current();}}
        private boolean current(){return !retired&&ticket==generation;}
        private void requireCurrent(){if(!current())throw new IllegalStateException("Obsolete session owner");}
        public GameSession load() throws Exception {
            synchronized(SessionRepository.this){
                requireCurrent();
                String saved = storage.read();
                var checkpoint = saved == null ? null : SessionJson.restoreCheckpoint(saved);
                var session = checkpoint == null ? null : checkpoint.session();
                durable = session == null ? null : session.save();
                durableHandCount = session == null ? 0 : session.achievementHands().size();
                var previous = checkpoint == null ? null : checkpoint.progress();
                progress = profile == null ? previous : previous != null && profile.equals(previous.profile())
                        ? previous : AchievementProgress.empty(profile);
                loaded = true;
                return session;
            }
        }
        public void commit(GameSession session) throws Exception {
            synchronized(SessionRepository.this){
                requireCurrent();
                if (!loaded && profile != null) load();
                var next = progress;
                var candidate = session.save();
                var hands = session.achievementHands();
                if (profile != null) {
                    int known = durable != null && durable.seed() == candidate.seed() && durable.players() == candidate.players()
                            ? durableHandCount : 0;
                    for (int index = known; index < hands.size(); index++) {
                        next = next.record(candidate.players(), candidate.seed(), hands.get(index), clock.instant());
                    }
                }
                storage.write("session.json", SessionJson.saved(session, next).toString());
                durable = candidate;
                durableHandCount = hands.size();
                progress = next;
            }
        }
        public AchievementProgress progress() {
            synchronized (SessionRepository.this) {
                requireCurrent();
                return progress;
            }
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

package ro.rentzlab.app.lifecycle;

/** IO port; a repository owner decides which generation may use it. */
public interface SessionStorage {
    String read() throws Exception;
    void write(String name, String text) throws Exception;
}

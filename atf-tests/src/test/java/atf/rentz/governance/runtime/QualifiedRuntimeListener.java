package atf.rentz.governance.runtime;

import atf.rentz.runtime.ClasspathInventory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/** An outer launcher attests the bytes before any tests and after their completion. */
public final class QualifiedRuntimeListener implements TestExecutionListener {
    private static final AtomicBoolean CLAIMED = new AtomicBoolean();
    private boolean owner;
    private boolean failed;
    private Path inventory;
    private Path receipt;
    private String token;
    private String classpath;
    private String testClasspath;
    private String inventoryHash;
    private ClasspathInventory.Snapshot before;

    @Override public void testPlanExecutionStarted(TestPlan testPlan) {
        String target = System.getProperty("rentz.runtime.inventory");
        if (target == null || !CLAIMED.compareAndSet(false, true)) return;
        owner = true;
        inventory = Path.of(target);
        receipt = Path.of(System.getProperty("rentz.runtime.attestation"));
        token = System.getProperty("rentz.runtime.token");
        testClasspath = System.getProperty("java.class.path");
        try {
            if (token == null || !token.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("Runtime token required");
            classpath = ClasspathInventory.executableTestClasspath(testClasspath, Thread.currentThread().getContextClassLoader());
            before = ClasspathInventory.capture(classpath);
            ClasspathInventory.writeNew(inventory, before);
            inventoryHash = ClasspathInventory.sha256(inventory);
        } catch (Exception failure) {
            recordFailure(failure);
        }
    }

    @Override public void testPlanExecutionFinished(TestPlan testPlan) {
        if (!owner || failed) return;
        try {
            if (!inventoryHash.equals(ClasspathInventory.sha256(inventory))
                    || !before.equals(ClasspathInventory.capture(classpath))) {
                throw new IllegalStateException("Runtime bytes changed while the test plan ran");
            }
            write("PASS", "");
        } catch (Exception failure) {
            recordFailure(failure);
        }
    }

    private void recordFailure(Exception failure) {
        failed = true;
        try { write("FAIL", failure.toString()); }
        catch (Exception recording) { System.err.println("RUNTIME-ATTESTATION: " + failure + "; receipt: " + recording); }
    }

    private void write(String status, String diagnostic) throws Exception {
        var value = new LinkedHashMap<String, Object>();
        value.put("schemaVersion", 1);
        value.put("status", status);
        value.put("token", token);
        value.put("inventorySha256", inventoryHash == null ? "" : inventoryHash);
        value.put("classpath", classpath);
        value.put("testClasspath", testClasspath);
        value.put("diagnostic", diagnostic);
        ClasspathInventory.writeNew(receipt, value);
    }
}

package ro.rentzlab.app.testing;

import com.google.gson.GsonBuilder;
import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.junit.function.ThrowingRunnable;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Captures comparisons at evaluation time; XML outcomes never manufacture expected/actual values. */
public final class UiAuditRule implements TestRule {
    public record Check(String name, String expected, String actual, boolean passed) { }
    private final List<Check> checks = new ArrayList<>();
    public void equal(String name, Object expected, Object actual) {
        checks.add(new Check(name, String.valueOf(expected), String.valueOf(actual), Objects.deepEquals(expected, actual)));
        Assert.assertEquals(name, expected, actual);
    }
    public void truth(String name, boolean actual) { equal(name, true, actual); }
    public <T extends Throwable> T rejects(String name, Class<T> type, ThrowingRunnable action) {
        try {
            T error = Assert.assertThrows(name, type, action);
            checks.add(new Check(name, type.getName(), error.getClass().getName(), true)); return error;
        } catch (AssertionError error) {
            checks.add(new Check(name, type.getName(), "Expected exception was not observed", false)); throw error;
        }
    }
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override public void evaluate() throws Throwable {
                Throwable failure = null;
                try { base.evaluate(); if (checks.isEmpty()) throw new AssertionError("An audited component needs evaluated comparisons"); }
                catch (Throwable error) { failure = error; throw error; }
                finally {
                    String target = System.getProperty("rentz.uiEvidence", "");
                    if (!target.isBlank()) {
                        var receipt = new LinkedHashMap<String, Object>();
                        receipt.put("class", description.getClassName()); receipt.put("method", description.getMethodName());
                        receipt.put("status", failure == null ? "PASS" : "FAIL"); receipt.put("failure", failure == null ? "" : failure.toString()); receipt.put("checks", checks);
                        Path root = Path.of(target); Files.createDirectories(root);
                        String file = (description.getClassName() + "." + description.getMethodName()).replaceAll("[^a-zA-Z0-9._-]", "_") + ".json";
                        try {
                            Files.write(root.resolve(file), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(receipt).getBytes(StandardCharsets.UTF_8));
                        } catch (Throwable publication) {
                            if (failure != null) failure.addSuppressed(publication); else throw publication;
                        }
                    }
                }
            }
        };
    }
}

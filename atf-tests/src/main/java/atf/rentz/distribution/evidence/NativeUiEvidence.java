package atf.rentz.distribution.evidence;

import atf.rentz.distribution.AtfReportingLibrary;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import ro.rentzlab.core.Contract;

import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.assertNoSymlinkPath;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.exactCount;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.externalRun;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.readExternalFile;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.safeFailure;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256;

/** Qualifies native gameplay independently of the report that records it. */
public final class NativeUiEvidence {
    private NativeUiEvidence() { }

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public static final int REQUIRED_COMPLETE_GAMES = 3;
    public static final int REQUIRED_DISTINCT_CONTRACTS = 3;

    public static void attach(Path root, String commit, String candidateVersion, ObjectNode uiGate, ObjectNode reportGate,
                              Path componentRun, Path suppliedBuildRun, Path suppliedUiRun) {
        if (suppliedBuildRun == null) { uiGate.put("status", "BLOCKED"); uiGate.put("actual", "A source-bound UI-sanity build receipt is required."); return; }
        String receiptHash = "";
        try {
            Path buildRun = externalRun(root, suppliedBuildRun);
            JsonNode build = JSON.readTree(readExternalFile(buildRun.resolve("ui-sanity-build-qualification.json"), 4L * 1024 * 1024));
            if (!build.path("status").asText().equals("PASS") || !build.path("sourceCommit").asText().equals(commit)
                    || !build.path("uiSanity").asBoolean(false) || !build.path("applicationId").asText().equals("ro.rentzlab.app.uisanity")
                    || exactCount(build.path("newAuditedCases")) < 1
                    || !build.path("versionName").asText().equals(candidateVersion)
                    || componentRun == null || !Path.of(build.path("componentRun").asText()).toRealPath().equals(componentRun.toRealPath()))
                throw new IOException("RAG-UI-BUILD-IDENTITY");
            AndroidSourceEvidence.verify(buildRun, componentRun, build);
            Path builder = root.resolve("scripts/build-rentz-android.ps1").normalize();
            assertNoSymlinkPath(builder);
            if (!builder.startsWith(root) || !Files.isRegularFile(builder, LinkOption.NOFOLLOW_LINKS)
                    || !sha256(Files.readAllBytes(builder)).equalsIgnoreCase(build.path("builderSha256").asText()))
                throw new IOException("RAG-UI-BUILD-TOOL-IDENTITY");
            Path uiApk = Path.of(build.path("apk").asText()).toAbsolutePath().normalize();
            if (!uiApk.startsWith(buildRun) || !Files.isRegularFile(uiApk, LinkOption.NOFOLLOW_LINKS)
                    || !uiApk.getFileName().toString().equals("rentz-lab-ui-sanity.apk"))
                throw new IOException("RAG-UI-APK-BOUNDARY");
            byte[] uiApkBytes = readExternalFile(uiApk, 256L * 1024 * 1024);
            if (!sha256(uiApkBytes).equalsIgnoreCase(build.path("apkSha256").asText())
                    || exactCount(build.path("apkBytes")) != uiApkBytes.length) throw new IOException("RAG-UI-APK-HASH");
            Path runner = root.resolve("atf-tests/tools/run-shared-ui-sanity.ps1").normalize();
            assertNoSymlinkPath(runner);
            if (!runner.startsWith(root) || !Files.isRegularFile(runner, LinkOption.NOFOLLOW_LINKS)) throw new IOException("RAG-UI-TOOL-IDENTITY");
            Path run = externalRun(root, suppliedUiRun);
            byte[] bytes = readExternalFile(run.resolve("result.json"), 8L * 1024 * 1024);
            receiptHash = sha256(bytes);
            JsonNode ui = JSON.readTree(bytes);
            if (!ui.path("developmentLedgerScope").asText().equals("RUN_ISOLATED_COPY"))
                throw new IOException("RAG-UI-LEARNING-ISOLATION");
            Path learningLedger = Path.of(ui.path("developmentLedger").asText()).toAbsolutePath().normalize();
            if (!learningLedger.startsWith(run) || !Files.isRegularFile(learningLedger, LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(learningLedger.resolveSibling(learningLedger.getFileName() + ".native-pending"), LinkOption.NOFOLLOW_LINKS)
                    || !ui.path("sharedDevelopmentLedgerUnchanged").asBoolean(false)
                    || !ui.path("sharedDevelopmentLedgerSha256Before").asText().matches("[a-f0-9]{64}")
                    || !ui.path("sharedDevelopmentLedgerSha256Before").asText().equals(ui.path("sharedDevelopmentLedgerSha256After").asText()))
                throw new IOException("RAG-UI-LEARNING-ISOLATION");
            byte[] learningBytes = readExternalFile(learningLedger, 8L * 1024 * 1024);
            if (!sha256(learningBytes).equalsIgnoreCase(ui.path("isolatedDevelopmentLedgerSha256After").asText())
                    || !ui.path("isolatedDevelopmentLedgerSha256Before").asText().matches("[a-f0-9]{64}")
                    || !ui.path("isolatedDevelopmentLedgerSha256Before").asText().equals(ui.path("sharedDevelopmentLedgerSha256Before").asText()))
                throw new IOException("RAG-UI-LEARNING-ISOLATION");
            if (!sha256(Files.readAllBytes(runner)).equalsIgnoreCase(ui.path("runnerSha256").asText()))
                throw new IOException("RAG-UI-TOOL-IDENTITY");
            AtfReportingLibrary reporting = AtfReportingLibrary.resolve(root);
            if (!ui.path("reportingLibrary").asText().equals(reporting.artifactName())
                    || !ui.path("reportingLibrarySha256").asText().equalsIgnoreCase(reporting.sha256()))
                throw new IOException("RAG-UI-REPORTING-LIBRARY");
            if (!ui.path("build").path("sourceCommit").asText().equals(commit)
                    || !ui.path("build").path("apkSha256").asText().equalsIgnoreCase(build.path("apkSha256").asText())
                    || !ui.path("apkSha256").asText().equalsIgnoreCase(build.path("apkSha256").asText()))
                throw new IOException("RAG-UI-SOURCE-APK");
            String status = ui.path("status").asText();
            if (status.equals("FAIL")) {
                uiGate.put("status", "FAIL"); uiGate.put("actual", safeUiFailure(ui));
                uiGate.put("receipt", "result.json"); uiGate.put("receiptSha256", receiptHash);
                reportGate.put("status", "BLOCKED"); reportGate.put("actual", "A failing UI receipt cannot qualify its Extent artifact."); return;
            }
            if (!status.equals("PASS") || !ui.path("consecutiveSubgames").asBoolean(false)
                    || !ui.path("package").asText().equals("ro.rentzlab.app.uisanity")
                    || !ui.path("displayAndPackagesPreserved").asBoolean(false) || !ui.path("leaseReleased").asBoolean(false)
                    || !ui.path("cleanupFailures").isArray() || !ui.path("cleanupFailures").isEmpty())
                throw new IOException("RAG-UI-CLOSEOUT");
            JsonNode contracts = ui.path("subgames");
            if (!contracts.isArray() || contracts.size() != REQUIRED_COMPLETE_GAMES || !contracts.get(0).asText().equals("RENTZ"))
                throw new IOException("RAG-UI-SUBGAMES");
            var distinct = new HashSet<Contract>();
            for (JsonNode contract : contracts) distinct.add(Contract.valueOf(contract.asText()));
            if (distinct.size() < REQUIRED_DISTINCT_CONTRACTS) throw new IOException("RAG-UI-CONTRACT-VARIETY");
            JsonNode audit = ui.path("gameAudit");
            if (!audit.path("status").asText().equals("PASS") || exactCount(audit.path("completedDeals")) != REQUIRED_COMPLETE_GAMES
                    || exactCount(audit.path("auditedTransitions")) < 1 || !audit.path("phase").asText().equals("DEAL_END"))
                throw new IOException("RAG-UI-ORACLE");
            JsonNode display = ui.path("display"), before = display.path("before"), after = display.path("after");
            JsonNode effective = display.path("effective"), compact = ui.path("compactConsecutive");
            if (!compact.isBoolean() || !compact.booleanValue() || !effective.path("viewport").asText().equals("720x1280")
                    || !effective.path("density").asText().equals("360") || !effective.path("fontScale").asText().equals("1.5"))
                throw new IOException("RAG-UI-COMPACT-PROFILE");
            if (!before.path("size").asText().matches("Physical size: [1-9][0-9]*x[1-9][0-9]*")
                    || !before.path("density").asText().matches("Physical density: [1-9][0-9]*")
                    || !before.path("font").asText().matches("[0-9]+(?:\\.[0-9]+)?")
                    || !before.equals(after)) throw new IOException("RAG-UI-DISPLAY-PRESERVATION");
            JsonNode checkpoints = ui.path("checkpoints");
            if (!checkpoints.isArray() || checkpoints.size() < REQUIRED_COMPLETE_GAMES * 3 + 4) throw new IOException("RAG-UI-CHECKPOINTS");
            for (JsonNode checkpoint : checkpoints) {
                if (checkpoint.path("title").asText().isBlank() || !checkpoint.path("image").asText().endsWith(".png")
                        || !checkpoint.path("hierarchy").asText().endsWith(".txt") || !checkpoint.path("checks").isArray()
                        || checkpoint.path("checks").isEmpty()) throw new IOException("RAG-UI-CHECKPOINT-SCHEMA");
                if (!checkpoint.path("logicalViewport").asText().equals("720x1280"))
                    throw new IOException("RAG-UI-CHECKPOINT-VIEWPORT");
                for (JsonNode check : checkpoint.path("checks"))
                    if (check.path("name").asText().isBlank() || !check.path("passed").asBoolean(false)
                            || !check.path("expected").isTextual() || !check.path("actual").isTextual())
                        throw new IOException("RAG-UI-CHECKPOINT-FAILED");
            }
            JsonNode completedGames = ui.path("completedGames");
            JsonNode auditHistory = audit.path("history");
            if (!completedGames.isArray() || completedGames.size() != REQUIRED_COMPLETE_GAMES
                    || !auditHistory.isArray() || auditHistory.size() != REQUIRED_COMPLETE_GAMES)
                throw new IOException("RAG-UI-COMPLETE-GAMES");
            for (int i = 0; i < REQUIRED_COMPLETE_GAMES; i++) {
                String contract = contracts.get(i).asText();
                JsonNode game = completedGames.get(i), history = auditHistory.get(i);
                if (exactCount(game.path("number")) != i + 1 || !game.path("contract").asText().equals(contract)
                        || !game.path("phase").asText().equals("DEAL_END") || exactCount(game.path("handNumber")) != i + 1
                        || exactCount(game.path("scoreCount")) != 4 || !game.path("seatGeometryVerified").asBoolean(false)
                        || !game.path("firstHumanMoveVerified").isBoolean() || !game.path("firstHumanMoveVerified").booleanValue()
                        || !game.path("completionVerified").asBoolean(false)
                        || !history.path("contract").asText().equals(contract) || exactCount(history.path("number")) != i + 1
                        || !history.path("points").isArray() || history.path("points").size() != 4)
                    throw new IOException("RAG-UI-COMPLETE-GAME-IDENTITY");
                requireUiCheckpoint(checkpoints, "Consecutive subgame " + (i + 1) + " starts: " + contract,
                        "Four-player table uses the same seat rectangles in this viewport");
                requireUiCheckpoint(checkpoints, "Consecutive subgame " + (i + 1) + " starts: " + contract,
                        "Contract is directly below hand progress");
                requireUiCheckpoint(checkpoints, "Subgame " + (i + 1) + " first human move remains visible",
                        "The same seat geometry survives a card play");
                requireUiCheckpoint(checkpoints, "Subgame " + (i + 1) + " completed: " + contract,
                        "One score value per player");
                requireUiCheckpoint(checkpoints, "Subgame " + (i + 1) + " completed: " + contract,
                        "Contract is directly below hand progress");
                if (contract.equals("RENTZ")) {
                    if (!game.path("rentzFinishOrderVerified").asBoolean(false))
                        throw new IOException("RAG-UI-RENTZ-COMPLETE");
                    requireUiCheckpoint(checkpoints, "Subgame " + (i + 1) + " completed: RENTZ", "Rentz finishing order contains each player");
                }
            }
            requireUiCheckpoint(checkpoints, "Score history contains the consecutive subgames", "All completed history rows are reachable");
            requireUiCheckpoint(checkpoints, "The match remains resumable", "Resume remains enabled");
            requireUiCheckpoint(checkpoints, "The match restores its final contract and scores", "Restored seat geometry");
            requireUiCheckpoint(checkpoints, "The confirmed rows remain after process restart", "Restarted history retains all completed subgames");
            requireUiCheckpoint(checkpoints, "Achievements open at the beginning of the learning path", "Onboarding comes before contract goals");
            requireUiCheckpoint(checkpoints, "Gameplay catalog opens", "Gameplay starts with the first manual hand");
            requireUiCheckpoint(checkpoints, "Manual settlement presents achievement progress", "First manually completed hand is unlocked");
            requireUiCheckpoint(checkpoints, "Manual settlement presents achievement progress", "The same panel includes partial progress");
            requireUiCheckpoint(checkpoints, "Manual settlement presents achievement progress", "The score title stays above the achievement panel");
            requireUiCheckpoint(checkpoints, "Manual settlement presents achievement progress", "Achievement panel has a distinct dismiss control");
            requireUiCheckpoint(checkpoints, "Resuming a saved settlement does not repeat achievement progress", "No achievement panel is replayed");
            requireUiCheckpoint(checkpoints, "Gameplay and training keep independent progress", "Confirmed manual play grants gameplay credit");
            requireUiCheckpoint(checkpoints, "Gameplay milestones survive process restart", "Same gameplay total after restart");
            requireUiCheckpoint(checkpoints, "Achievement search reports an empty result", "Empty result is visible and can be reset");
            requireUiCheckpoint(checkpoints, "Achievements record manual and assisted play after settlement", "Manual and assisted milestones are recorded separately");
            requireUiCheckpoint(checkpoints, "Achievements record manual and assisted play after settlement", "Complete catalog records new credit after settlement");
            requireUiCheckpoint(checkpoints, "Search and status filter reveal the unlocked lesson", "Combined query returns exactly one achievement");
            requireUiCheckpoint(checkpoints, "Achievement details explain eligibility for the recent hand", "Eligibility contains rule, counting unit and recent context");
            requireUiCheckpoint(checkpoints, "Achievements and local identity survive restart without duplicate credit", "Same achievements total after restart");
            requireUiCheckpoint(checkpoints, "Achievements and local identity survive restart without duplicate credit", "Restart leaves the durable checkpoint byte-for-byte unchanged");
            requireUiCheckpoint(checkpoints, "Renaming the local profile retains achievement progress", "Same achievements total after rename");
            uiGate.put("status", "PASS"); uiGate.put("actual", "Three complete native games across three distinct contracts reached DEAL_END. Replay, seat geometry, history, restart and achievement browsing passed.");
            uiGate.put("receipt", "result.json"); uiGate.put("receiptSha256", receiptHash);

            Path extent = Path.of(ui.path("extentReport").asText()).toAbsolutePath().normalize();
            if (!extent.startsWith(run)) throw new IOException("RAG-UI-REPORT-BOUNDARY");
            byte[] htmlBytes = readExternalFile(extent, 128L * 1024 * 1024);
            if (!sha256(htmlBytes).equalsIgnoreCase(ui.path("extentReportSha256").asText())) throw new IOException("RAG-UI-REPORT-HASH");
            String html = new String(htmlBytes, StandardCharsets.UTF_8);
            NativeExtentEvidence.verify(html, root, ui, checkpoints);
            reportGate.put("status", "PASS"); reportGate.put("actual", "Each native Extent checkpoint preserves its original inline PNG and every expected/actual comparison in order.");
            reportGate.put("receipt", run.relativize(extent).toString().replace('\\', '/')); reportGate.put("receiptSha256", sha256(htmlBytes));
            reportGate.put("artifactBytes", htmlBytes.length);
            reportGate.put("artifactSourceCommit", commit);
        } catch (Exception invalid) {
            if (!uiGate.path("status").asText().equals("PASS") && !uiGate.path("status").asText().equals("FAIL")) {
                uiGate.put("status", "BLOCKED");
                uiGate.put("actual", safeFailure(invalid, "RAG-UI-RECEIPT-INVALID"));
            }
            reportGate.put("status", "BLOCKED"); reportGate.put("actual", safeFailure(invalid, "RAG-UI-REPORT-INVALID"));
            if (!receiptHash.isBlank()) { uiGate.put("receipt", "result.json"); uiGate.put("receiptSha256", receiptHash); }
        }
    }

    private static void requireUiCheckpoint(JsonNode checkpoints, String title, String assertion) throws IOException {
        for (JsonNode checkpoint : checkpoints) {
            if (!checkpoint.path("title").asText().equals(title)) continue;
            for (JsonNode check : checkpoint.path("checks"))
                if (check.path("name").asText().equals(assertion) && check.path("passed").asBoolean(false)
                        && check.path("expected").asText().equals(check.path("actual").asText())) return;
            throw new IOException("RAG-UI-CHECKPOINT-EVIDENCE: " + title);
        }
        throw new IOException("RAG-UI-CHECKPOINT-MISSING: " + title);
    }

    private static String safeUiFailure(JsonNode ui) {
        String value = ui.path("failure").asText("UI run failed without a diagnostic.").replaceAll("\\s+", " ").trim();
        return value.length() <= 300 ? value : value.substring(0, 300) + "…";
    }

}

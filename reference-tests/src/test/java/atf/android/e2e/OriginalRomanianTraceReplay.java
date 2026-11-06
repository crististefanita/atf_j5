package atf.android.e2e;

import atf.android.e2e.support.AndroidUiFlowTrace;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfGherkinKeyword;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.ui.model.AtfUiActionRecord;
import atf.reporting.ui.model.AtfUiEvidenceSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Reimports captured evidence from the same Android game; this helper sends no device commands. */
final class OriginalRomanianTraceReplay {
    record Replay(String priorFailure,AtfReportStatus priorStatus,int checkpoints,int maxActionSequence,Path recoveryMetadata) { }
    private record Captured(String phase,String keyword,String title,String target,Path screenshot,
            List<AtfUiActionRecord> actions,List<AtfAssertion> assertions,AtfReportStatus status,List<Path> artifacts) { }
    private OriginalRomanianTraceReplay() { }

    static Replay append(Path priorManifest,Path outputDirectory,AndroidUiFlowTrace trace)throws Exception {
        Path out=outputDirectory.toAbsolutePath().normalize(),source=priorManifest.toAbsolutePath().normalize();
        require(trace.appPackage().equals("com.qsoft.android.whist"),"Recovery target must be the original app");
        require(trace.manifest().getParent().equals(out),"Recovery output must be the new trace directory");
        require(!source.equals(trace.manifest()),"Read the preserved prior manifest, not the newly created trace manifest");
        require(trace.phaseSnapshots().isEmpty()&&trace.pendingActions().isEmpty()&&trace.failure().isBlank(),"Recovery requires a fresh trace");
        byte[] bytes=Files.readAllBytes(source);var p=new Properties();p.load(new ByteArrayInputStream(bytes));
        require(text(p,"format.version").equals("1"),"Unsupported prior flow format");
        require(text(p,"flow.appPackage").equals("com.qsoft.android.whist"),"Prior flow must belong to the original app");
        AtfReportStatus priorStatus=status(text(p,"flow.status"));String priorFailure=present(p,"flow.failure");
        var captured=new ArrayList<Captured>();var sequences=new HashSet<Integer>();var phases=new HashSet<String>();int maximum=0;
        for(int phase=0;phase<count(p,"flow.phaseCount");phase++) {
            String phaseKey="phase."+phase,name=text(p,phaseKey+".name");require(phases.add(name),"Duplicate prior phase name");
            for(int step=0;step<count(p,phaseKey+".stepCount");step++) {
                String key=phaseKey+".step."+step,keyword=text(p,key+".keyword");
                try{AtfGherkinKeyword.valueOf(keyword);}catch(IllegalArgumentException invalid){throw new IOException("Unknown prior checkpoint keyword: "+keyword,invalid);}
                var actions=new ArrayList<AtfUiActionRecord>();
                for(int a=0;a<count(p,key+".actionCount");a++){var action=action(p,key+".action."+a,sequences);actions.add(action);maximum=Math.max(maximum,action.sequence());}
                var assertions=new ArrayList<AtfAssertion>();
                for(int a=0;a<count(p,key+".assertionCount");a++) {
                    String prefix=key+".assertion."+a,passed=present(p,prefix+".passed");require(passed.equals("true")||passed.equals("false"),"Strict assertion boolean required");
                    assertions.add(new AtfAssertion(text(p,prefix+".name"),present(p,prefix+".expected"),present(p,prefix+".actual"),Boolean.parseBoolean(passed),status(text(p,prefix+".status"))));
                }
                var artifacts=new ArrayList<Path>();for(int a=0;a<count(p,key+".artifactCount");a++)artifacts.add(artifact(out,text(p,key+".artifact."+a)));
                String image=present(p,key+".screenshot");
                captured.add(new Captured(name,keyword,text(p,key+".title"),text(p,key+".target"),image.isBlank()?null:artifact(out,image),List.copyOf(actions),List.copyOf(assertions),status(text(p,key+".status")),List.copyOf(artifacts)));
            }
        }
        var pending=new ArrayList<AtfUiActionRecord>();
        for(int a=0;a<count(p,"flow.pendingActionCount");a++){var action=action(p,"flow.pendingAction."+a,sequences);pending.add(action);maximum=Math.max(maximum,action.sequence());}
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Path metadata=out.resolve("original-trace-recovery-"+digest.substring(0,12)+".json");
        var recovery=new LinkedHashMap<String,Object>();recovery.put("schemaVersion",1);recovery.put("priorManifest",source.toString());recovery.put("priorManifestSha256",digest);
        recovery.put("priorFlowId",text(p,"flow.id"));recovery.put("priorStatus",priorStatus.name());recovery.put("priorFailure",priorFailure);recovery.put("capturedCheckpoints",captured.size());
        recovery.put("maximumPriorActionSequence",maximum);recovery.put("priorPendingActions",pending);
        recovery.put("scope","The prior failed attempt and its report/native result remain preserved. Captured checkpoint/action/assertion statuses are imported unchanged. Prior uncaught failure and uncheckpointed actions remain recovery metadata, not a fabricated failure or success of the new continuation.");
        Files.writeString(metadata,new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(recovery)+"\n",StandardOpenOption.CREATE_NEW);
        for(var c:captured)trace.checkpoint(c.phase(),c.keyword(),c.title(),c.target(),c.screenshot(),c.actions(),c.assertions(),c.status(),c.artifacts());
        // Explicit checkpoint actions do not update the trace's counter. Starting from a fresh zero,
        // consume exactly maximum values: the next caller receives maximum+1, never a reused number.
        for(int i=0;i<maximum;i++)trace.nextActionSequence();
        return new Replay(priorFailure,priorStatus,captured.size(),maximum,metadata);
    }

    private static AtfUiActionRecord action(Properties p,String key,Set<Integer> sequences)throws IOException {
        int sequence=count(p,key+".sequence");require(sequence>0&&sequences.add(sequence),"Prior action sequences must be positive and unique");
        long duration;try{duration=Long.parseLong(text(p,key+".durationMillis"));}catch(NumberFormatException invalid){throw new IOException("Invalid prior action duration",invalid);}
        require(duration>=0,"Prior action duration cannot be negative");
        AtfUiEvidenceSource source;try{source=AtfUiEvidenceSource.valueOf(text(p,key+".source"));}catch(IllegalArgumentException invalid){throw new IOException("Unknown prior action evidence source",invalid);}
        return new AtfUiActionRecord(sequence,text(p,key+".operation"),text(p,key+".target"),present(p,key+".expected"),present(p,key+".observed"),duration,status(text(p,key+".status")),source);
    }
    private static Path artifact(Path out,String relative)throws IOException {
        Path ref=Path.of(relative);require(!ref.isAbsolute(),"Prior evidence path must be relative");Path file=out.resolve(ref).normalize();
        require(file.startsWith(out)&&Files.isRegularFile(file),"Prior evidence must exist under the new output directory: "+relative);
        require(file.toRealPath().startsWith(out.toRealPath()),"Prior evidence cannot escape through a symbolic link");return file;
    }
    private static AtfReportStatus status(String value)throws IOException {try{return AtfReportStatus.valueOf(value);}catch(IllegalArgumentException invalid){throw new IOException("Unknown prior report status: "+value,invalid);}}
    private static int count(Properties p,String key)throws IOException {try{int value=Integer.parseInt(text(p,key));require(value>=0,"Prior count cannot be negative: "+key);return value;}catch(NumberFormatException invalid){throw new IOException("Invalid prior integer: "+key,invalid);}}
    private static String present(Properties p,String key)throws IOException {String value=p.getProperty(key);require(value!=null,"Missing prior flow field: "+key);return value;}
    private static String text(Properties p,String key)throws IOException {String value=present(p,key);require(!value.isBlank(),"Blank prior flow field: "+key);return value;}
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}

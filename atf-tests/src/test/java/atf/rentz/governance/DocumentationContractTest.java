package atf.rentz.governance;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentationContractTest {
    static Path root(){Path p=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(p!=null&&!Files.isDirectory(p.resolve("config")))p=p.getParent();if(p==null)throw new IllegalStateException("Project root absent");return p;}
    @Test void obsoleteGameDocumentsCannotRemainInTheActiveDocumentationRoots(){
        assertTrue(Files.isRegularFile(root().resolve("docs/README.md")),"Governance requires a complete documentation snapshot");
        for(String path:List.of("docs/ORIGINAL_INSTRUCTIONS_COVERAGE.md","docs/ATF_Android_Whist_Testing.md","docs/ORIGINAL_ROMANIAN_HARD_LIVE.md","docs/feedback/ORIGINAL_ROMANIAN_LIVE_Feedback.md","docs/feedback/REFERENCE_MATCH_Feedback.md"))
            assertFalse(Files.exists(root().resolve(path)),"Historical material needs its own classified inventory: "+path);
    }
    @Test void moduleExamplesUseRegisteredLaunchersAndStateTheirShellAndJdk()throws Exception{
        for(String path:List.of("rentz-lab-app/README.md","rentz-lab-app/game-core/README.md","rentz-lab-app/app/README.md")){
            String text=Files.readString(root().resolve(path));assertTrue(text.contains("run-rentz-components.ps1"),path);
            assertTrue(text.contains("PowerShell 7")&&text.contains("JDK 21"),path);
            assertFalse(text.contains("copie temporară completă"),"An unregistered copy is not an executable infrastructure contract: "+path);
        }
    }
    @Test void activeAndHistoricalDocumentsHaveAnExecutableRetentionInventory()throws Exception{
        assertTrue(Files.isRegularFile(root().resolve("config/documentation-policy.json")),"Documentation scope and retention must be executable");
        validate(policy(),snapshot());
    }
    @Test void retentionCannotHideUnclassifiedDocumentsChangedBytesOrEscapedPaths()throws Exception{
        var input=snapshot();input.put("docs/forgotten.md","# Forgotten".getBytes());
        assertThrows(IllegalArgumentException.class,()->validate(policy(),input));
        var changed=snapshot();String archived=policy().required("historical").get(0).required("path").textValue();changed.put(archived,new byte[]{1});
        assertThrows(IllegalArgumentException.class,()->validate(policy(),changed));
        var escaped=policy();((ObjectNode)escaped.required("historical").get(0)).put("path","docs/history/contract-whist/../../../README.md");
        assertThrows(IllegalArgumentException.class,()->validate(escaped,snapshot()));
        var missing=policy();((com.fasterxml.jackson.databind.node.ArrayNode)missing.required("historical")).remove(0);
        assertThrows(IllegalArgumentException.class,()->validate(missing,snapshot()));
    }
    @Test void launcherRoutesCannotAdvertiseUnregisteredScriptsOrWrongCollections()throws Exception{
        var missing=policy();((ObjectNode)missing.required("launchers").get(0)).put("script","scripts/unregistered.ps1");
        assertThrows(IllegalArgumentException.class,()->validate(missing,snapshot()));
        var wrong=policy();((ObjectNode)wrong.required("launchers").get(0)).put("collection","unknown");
        assertThrows(IllegalArgumentException.class,()->validate(wrong,snapshot()));
    }
    private static JsonNode policy()throws IOException{return new ObjectMapper().readTree(root().resolve("config/documentation-policy.json").toFile());}
    private static Map<String,byte[]> snapshot()throws IOException{
        var files=new HashMap<String,byte[]>();
        try(var walk=Files.walk(root().resolve("docs"))){for(var file:walk.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".md")).toList())files.put(root().relativize(file).toString().replace('\\','/'),Files.readAllBytes(file));}
        for(String file:List.of("rentz-lab-app/README.md","rentz-lab-app/game-core/README.md","rentz-lab-app/app/README.md"))files.put(file,Files.readAllBytes(root().resolve(file)));
        return files;
    }
    private static String path(JsonNode node){
        if(!node.isTextual()||!node.textValue().matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_.-]+)+")||Arrays.asList(node.textValue().split("/")).contains(".."))throw new IllegalArgumentException("Safe project path required");
        return node.textValue();
    }
    private static void require(boolean condition,String reason){if(!condition)throw new IllegalArgumentException(reason);}
    private static void validate(JsonNode policy,Map<String,byte[]> files)throws Exception{
        require(policy.required("schemaVersion").intValue()==1,"Documentation schema");
        var retention=policy.required("retention");
        require(retention.required("mode").asText().equals("PRESERVE_REFERENCED_HISTORY")&&retention.required("deletion").asText().equals("EXPLICIT_REVIEW_ONLY")&&retention.required("evidence").asText().equals("KEEP_EXTERNAL_TARGETS")&&retention.required("originals").asText().equals("EXTERNAL_IMMUTABLE_AUDIT"),"Retention contract");
        String history=path(policy.required("historyRoot"));var historical=new HashSet<String>();var originals=new HashSet<String>();
        for(var row:policy.required("historical")){
            String file=path(row.required("path")),original=path(row.required("originalPath"));
            require(file.startsWith(history+"/")&&historical.add(file)&&originals.add(original)&&!files.containsKey(original)&&files.containsKey(file),"Historical inventory");
            require(row.required("status").asText().equals("HISTORICAL_SNAPSHOT")&&row.required("originalSha256").asText().matches("[a-f0-9]{64}"),"Historical identity");
            require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(files.get(file))).equals(row.required("sha256").asText()),"Archived bytes changed");
        }
        require(!historical.isEmpty(),"Explicit historical scope");var active=new HashSet<String>();
        for(var node:policy.required("activeFiles")){String file=path(node);require(active.add(file)&&files.containsKey(file)&&!historical.contains(file),"Active file inventory");}
        for(var node:policy.required("activeRoots")){
            String directory=path(node);require(!directory.startsWith(history)&&!history.startsWith(directory),"History cannot become an active root");
            for(String file:files.keySet())if(file.startsWith(directory+"/"))require(active.add(file)&&!historical.contains(file),"Overlapping active inventory");
        }
        for(String file:files.keySet())if(file.startsWith("docs/"))require(active.contains(file)||historical.contains(file),"Unclassified document: "+file);
        var json=new ObjectMapper();var infrastructure=json.readTree(root().resolve("config/infrastructure-workspaces.json").toFile());var registered=new HashSet<String>();infrastructure.required("producerScripts").forEach(n->registered.add(n.asText()));
        var collections=json.readTree(root().resolve("config/test-collections.json").toFile());var launchers=new HashSet<String>();
        for(var row:policy.required("launchers")){
            String doc=path(row.required("document")),script=path(row.required("script")),collection=row.required("collection").asText();
            require(launchers.add(doc)&&files.containsKey(doc)&&registered.contains(script)&&Files.isRegularFile(root().resolve(script)),"Unregistered launcher");
            require(collections.required("collections").has(collection),"Undeclared collection");
            String text=new String(files.get(doc),java.nio.charset.StandardCharsets.UTF_8);
            require(text.contains(script.replace('/','\\')+" -Collection "+collection)&&text.contains("rădăcina proiectului")&&text.contains(row.required("shell").asText())&&text.contains(row.required("jdk").asText()),"Incomplete startup route");
            require(row.required("sdk").isBoolean()&&(!row.get("sdk").booleanValue()||text.contains("SDK Android")),"SDK precondition");
        }
        require(launchers.equals(Set.of("rentz-lab-app/README.md","rentz-lab-app/game-core/README.md","rentz-lab-app/app/README.md")),"Module route population");
    }
}

package atf.rentz.libraries;

import atf.testkit.AtfIntegrationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.jar.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@AtfIntegrationTest
class LibraryAdoptionTest {
    @TempDir Path fixtures;
    private Path fixture()throws Exception{
        Path target=Files.createDirectory(fixtures.resolve(UUID.randomUUID().toString()));Files.createDirectories(target.resolve("config"));
        Files.copy(root().resolve("pom.xml"),target.resolve("pom.xml"));Files.copy(root().resolve("library-provenance.json"),target.resolve("library-provenance.json"));
        Files.copy(root().resolve("config/library-adoption.json"),target.resolve("config/library-adoption.json"));Files.createDirectory(target.resolve("lib"));
        try(var files=Files.list(root().resolve("lib"))){for(Path file:files.toList())Files.copy(file,target.resolve("lib").resolve(file.getFileName()));}
        return target;
    }
    private void rejects(Path root,String code){assertEquals(code,assertThrows(IllegalStateException.class,()->LibraryAdoption.inspect(root)).getMessage());}
    static Path root(){Path p=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(p!=null&&!Files.isDirectory(p.resolve("lib")))p=p.getParent();if(p==null)throw new IllegalStateException("Consumer root absent");return p;}
    @Test void provenanceIdentifiesThisConsumerRatherThanInheritingAnotherAcceptance()throws Exception{
        var provenance=new ObjectMapper().readTree(root().resolve("library-provenance.json").toFile());
        assertEquals("atf_j5",provenance.required("consumer").asText(),"Local acceptance must belong to this consumer");
    }
    @Test void theCurrentFivePhysicalPairsHaveAnExplicitConsumerAdoptionGate()throws Exception{
        assertTrue(Files.isRegularFile(root().resolve("config/library-adoption.json")),"A permanent, executable consumer manifest is required");
        var pairs=LibraryAdoption.inspect(root());assertEquals(5,pairs.size());
        assertTrue(pairs.stream().anyMatch(pair -> pair.coordinate().equals("atf.api:atf-reporting:1.0.0-j24")),
                "Consumer adoption must follow the root POM's current reporting version, not historical provenance");
        for(var pair:pairs)LibraryAdoption.loaded(pair,Class.forName(pair.marker()));
    }
    @Test void alteredJarAndPomCannotPassWithOldProvenance()throws Exception{
        for(String extension:List.of("jar","pom")){Path folder=fixture();var pair=LibraryAdoption.inspect(folder).getFirst();Files.writeString(extension.equals("jar")?pair.jar():pair.pom(),"altered");rejects(folder,extension.equals("jar")?"LIB-JAR-HASH":"LIB-POM-HASH");}
    }
    @Test void extraVersionsDirectoriesAndMissingGuidesAreRefused()throws Exception{
        Path extra=fixture();Files.writeString(extra.resolve("lib/atf-core-obsolete.jar"),"old");rejects(extra,"LIB-FLAT-LAYOUT");
        Path nested=fixture();Files.createDirectory(nested.resolve("lib/repository"));rejects(nested,"LIB-FLAT-LAYOUT");
        Path missing=fixture();Files.delete(missing.resolve("lib/README.Mock.md"));rejects(missing,"LIB-FLAT-LAYOUT");
    }
    @Test void sourceShadowAndChangedFeedbackOwnershipAreRefused()throws Exception{
        Path shadow=fixture();Files.createDirectory(shadow.resolve("atf-core"));rejects(shadow,"LIB-SOURCE-SHADOW");
        Path owner=fixture();var p=(ObjectNode)LibraryAdoption.JSON.readTree(owner.resolve("library-provenance.json").toFile());((ObjectNode)p.required("platformLibraryOwnership")).put("feedbackOwner","consumer");LibraryAdoption.JSON.writeValue(owner.resolve("library-provenance.json").toFile(),p);rejects(owner,"LIB-OWNERSHIP");
    }
    @Test void unsafeVersionAndDuplicatePomVersionAreRefusedBeforePathResolution()throws Exception{
        for(String value:List.of("../outside","one</atf.core.version><atf.core.version>two")){
            Path folder=fixture();Path pom=folder.resolve("pom.xml");String text=Files.readString(pom).replaceFirst("<atf.core.version>[^<]+</atf.core.version>","<atf.core.version>"+value+"</atf.core.version>");Files.writeString(pom,text);
            rejects(folder,value.startsWith("..")?"LIB-VERSION":"LIB-POM-FIELD:atf.core.version");
        }
    }
    @Test void untrustedNativePathsAndWrongConsumerAreRefused()throws Exception{
        Path bad=fixture();var p=(ObjectNode)LibraryAdoption.JSON.readTree(bad.resolve("library-provenance.json").toFile());((ObjectNode)p.required("libraries").get(0).required("producerInputs").required("nativeTests").get(0)).put("path","../outside.java");LibraryAdoption.JSON.writeValue(bad.resolve("library-provenance.json").toFile(),p);rejects(bad,"LIB-NATIVE-CATALOG");
        Path foreign=fixture();p=(ObjectNode)LibraryAdoption.JSON.readTree(foreign.resolve("library-provenance.json").toFile());p.put("consumer","other");LibraryAdoption.JSON.writeValue(foreign.resolve("library-provenance.json").toFile(),p);rejects(foreign,"LIB-CONSUMER");
    }
    @Test void rehashingAnArchiveCannotHideItsDifferentEmbeddedPom()throws Exception{
        Path folder=fixture();var pair=LibraryAdoption.inspect(folder).getFirst();Path replacement=folder.resolve("changed.jar");
        try(var input=new JarFile(pair.jar().toFile());var output=new JarOutputStream(Files.newOutputStream(replacement))){for(var entry:input.stream().toList()){
            output.putNextEntry(new JarEntry(entry.getName()));if(!entry.isDirectory())output.write(entry.getName().endsWith("/pom.xml")?"<project/>".getBytes(java.nio.charset.StandardCharsets.UTF_8):input.getInputStream(entry).readAllBytes());output.closeEntry();
        }}Files.move(replacement,pair.jar(),StandardCopyOption.REPLACE_EXISTING);
        var p=(ObjectNode)LibraryAdoption.JSON.readTree(folder.resolve("library-provenance.json").toFile());((ObjectNode)p.required("libraries").get(0).required("files").required("jar")).put("sha256",LibraryAdoption.hash(pair.jar()));LibraryAdoption.JSON.writeValue(folder.resolve("library-provenance.json").toFile(),p);rejects(folder,"LIB-EMBEDDED-POM");
    }
    @Test void classpathOriginIsComparedToBytesRatherThanJustAClassName()throws Exception{
        var pair=LibraryAdoption.inspect(root()).getFirst();var wrong=new LibraryAdoption.Pair(pair.name(),pair.coordinate(),pair.jar(),pair.pom(),"0".repeat(64),pair.pomHash(),pair.marker());
        assertEquals("LIB-CODE-SOURCE",assertThrows(IllegalStateException.class,()->LibraryAdoption.loaded(wrong,Class.forName(pair.marker()))).getMessage());
    }
    @Test void concatenatedJsonDocumentsAndFractionalSchemaVersionsAreRefused()throws Exception{
        Path trailing=fixture();Path manifest=trailing.resolve("config/library-adoption.json");Files.writeString(manifest,Files.readString(manifest)+"\n{}\n");
        assertThrows(Exception.class,()->LibraryAdoption.inspect(trailing),"Trailing documents cannot be ignored");
        Path fractional=fixture();manifest=fractional.resolve("config/library-adoption.json");Files.writeString(manifest,Files.readString(manifest).replace("\"schemaVersion\": 1","\"schemaVersion\": 1.5"));
        assertThrows(Exception.class,()->LibraryAdoption.inspect(fractional),"A fractional schema cannot be truncated");
    }
}

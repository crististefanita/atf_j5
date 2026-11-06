package atf.rentz.governance;

import atf.core.files.AtfJavaSourceInventory;
import atf.core.files.AtfJavaSourceReferences;
import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class SourceLayoutTest {
    @Test void androidCoreCannotCallCollectionFactoriesUnavailableWithoutDesugaring()throws Exception{
        var forbidden=java.util.regex.Pattern.compile("\\b(?:List|Set|Map)\\s*\\.\\s*(?:of|copyOf|ofEntries)\\s*\\(");
        assertTrue(forbidden.matcher("List.copyOf(values)").find());assertTrue(forbidden.matcher("java.util.Set.of(value)").find());
        assertFalse(forbidden.matcher("EnumSet.copyOf(values)").find());
        Path base=project().resolve("rentz-lab-app/game-core/src/main/java");var findings=new ArrayList<String>();
        try(var walk=Files.walk(base)){for(Path file:walk.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java")).toList()){
            if(forbidden.matcher(AtfJavaSourceReferences.codeOnly(Files.readString(file))).find())findings.add(base.relativize(file).toString());
        }}
        assertTrue(findings.isEmpty(),()->"Collection factories exceed minSdk without core-library desugaring: "+findings);
    }
    private static Path project(){
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null&&!Files.isRegularFile(root.resolve("config/java-source-layout.json")))root=root.getParent();
        if(root==null)throw new IllegalStateException("Declared Java source layout is missing");
        return root;
    }
    private static JsonNode policy(Path root)throws Exception{return new ObjectMapper().readTree(root.resolve("config/java-source-layout.json").toFile());}
    private static List<String> strings(JsonNode values){var result=new ArrayList<String>();values.forEach(v->result.add(v.asText()));return result;}
    private static Path source(Path root,JsonNode entry){
        Path result=root.resolve(entry.required("path").asText()).normalize();
        if(!result.startsWith(root)||!Files.isDirectory(result))throw new IllegalArgumentException("Invalid source root: "+entry);
        return result;
    }
    @Test void everyDeclaredJavaSourceHasAPackageAndACompiledSourceSet()throws Exception{
        Path root=project();var policy=policy(root);var findings=new ArrayList<String>();int count=0;
        for(var entry:policy.required("roots")){
            Path base=source(root,entry);var packages=AtfJavaSourceInventory.packages(base);
            try(var walk=Files.walk(base)){for(Path file:walk.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java")).toList()){
                count++;String relative=base.relativize(file).toString().replace('\\','/'),text=Files.readString(file);
                var forbidden=strings(entry.required("forbidden"));
                if(entry.required("role").asText().equals("tools"))findings.add("GOV-UNCOMPILED-SOURCE: "+root.relativize(file));
                if(entry.required("role").asText().equals("main"))for(var boundary:policy.required("productionBoundaries")){
                    if(packages.getOrDefault(boundary.required("package").asText(),List.of()).contains(file.toAbsolutePath()))forbidden.addAll(strings(boundary.required("forbidden")));
                }
                for(String finding:SourceLayoutRules.inspect(relative,text,forbidden))findings.add(root.relativize(file)+": "+finding);
            }}
        }
        assertTrue(count>0,"Source inventory cannot be empty");assertTrue(findings.isEmpty(),()->String.join("\n",findings));
    }
    @Test void everySourceSetRespectsTheEightFileCohesionLimitWithoutWaivers()throws Exception{
        Path root=project();var policy=policy(root);var findings=new ArrayList<String>();
        for(var entry:policy.required("roots"))AtfJavaSourceInventory.packages(source(root,entry)).forEach((name,files)->{
            if(SourceLayoutRules.oversized(files,policy.required("packageLimit").intValue()))findings.add(entry.required("path").asText()+":"+name+"="+files.size());
        });
        assertTrue(findings.isEmpty(),()->String.join("\n",findings));
    }
    @Test void reactorAndBinaryOwnershipMatchTheDeclaredPerimeter()throws Exception{
        Path root=project();var factory=DocumentBuilderFactory.newInstance();factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        var document=factory.newDocumentBuilder().parse(root.resolve("pom.xml").toFile()).getDocumentElement();var modules=new ArrayList<String>();
        for(var node=document.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element element&&element.getTagName().equals("modules"))for(var child=element.getFirstChild();child!=null;child=child.getNextSibling())if(child instanceof Element module&&module.getTagName().equals("module"))modules.add(module.getTextContent().strip());
        assertEquals(strings(policy(root).required("mavenModules")),modules);
        for(String name:List.of("atf-core","atf-reporting","atf-console","atf-mock","atf-test-kit","atf-reporting-tests"))assertFalse(Files.exists(root.resolve(name)),name);
    }
    @Test void lexicalControlsRejectMissingOrWrongPackagesDuplicateImportsAndRealDependencies(){
        assertEquals(List.of("GOV-PACKAGE-MISSING"),SourceLayoutRules.inspect("C.java","class C {}",List.of()));
        assertEquals(List.of("GOV-PACKAGE-PATH"),SourceLayoutRules.inspect("a/C.java","package b; class C {}",List.of()));
        assertEquals(List.of("GOV-DUPLICATE-IMPORT"),SourceLayoutRules.inspect("a/C.java","package a; import java.util.List; import java . util . List; class C {}",List.of()));
        assertEquals(List.of("GOV-BOUNDARY:ro.rentzlab.ai"),SourceLayoutRules.inspect("a/C.java","package a; import ro.rentzlab.ai.Policy; class C {}",List.of("ro.rentzlab.ai")));
        assertTrue(SourceLayoutRules.inspect("a/C.java","package a; class C { String example=\"ro.rentzlab.ai.Policy\"; } // ro.rentzlab.ai.Policy",List.of("ro.rentzlab.ai")).isEmpty());
    }
    @Test void theNinthFileFailsAndTheLimitCannotBeRaised(){
        var files=java.util.stream.IntStream.range(0,9).mapToObj(i->"Type"+i+".java").toList();
        assertFalse(SourceLayoutRules.oversized(files.subList(0,8),8));assertTrue(SourceLayoutRules.oversized(files,8));
        assertThrows(IllegalArgumentException.class,()->SourceLayoutRules.oversized(files,9));
    }
    @Test void javaFilesCannotHideOutsideTheDeclaredSourceRoots()throws Exception{
        Path root=project();var policy=policy(root);var roots=new ArrayList<Path>();for(var entry:policy.required("roots"))roots.add(source(root,entry));
        var excluded=strings(policy.required("excludedTrees")).stream().map(root::resolve).toList();
        assertTrue(SourceLayoutRules.uncovered(root,roots,excluded).isEmpty(),"Every active Java file needs a declared source set");
    }
    @Test void coverageControlIgnoresOnlyTheExactOutputTreeAndFindsUnregisteredSources(@TempDir Path root)throws Exception{
        Path source=root.resolve("module/src/main/java"),output=root.resolve("module/target"),unregistered=root.resolve("tools/Loose.java");
        Files.createDirectories(source.resolve("legal/target"));Files.createDirectories(output);Files.createDirectories(unregistered.getParent());
        Files.writeString(source.resolve("legal/target/C.java"),"package legal.target; class C {}");Files.writeString(output.resolve("Generated.java"),"generated");Files.writeString(unregistered,"class Loose {}");
        assertEquals(List.of(unregistered),SourceLayoutRules.uncovered(root,List.of(source),List.of(output)));
        assertTrue(SourceLayoutRules.uncovered(root,List.of(source,unregistered.getParent()),List.of(output)).isEmpty());
    }
}

package atf.rentz.libraries;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Consumer-owned acceptance of physical pairs; never builds a common implementation. */
final class LibraryAdoption {
    static final ObjectMapper JSON=new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static final List<String> NAMES=List.of("core","reporting","console","mock","test-kit");
    record Pair(String name,String coordinate,Path jar,Path pom,String jarHash,String pomHash,String marker){}
    static void require(boolean condition,String code){if(!condition)throw new IllegalStateException(code);}
    static String hash(Path file)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}
    static Element xml(byte[] bytes)throws Exception{
        var f=DocumentBuilderFactory.newInstance();f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
    }
    static String child(Element element,String name){var values=new ArrayList<String>();for(var n=element.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&e.getTagName().equals(name))values.add(e.getTextContent().strip());require(values.size()==1,"LIB-POM-FIELD:"+name);return values.getFirst();}
    static void fields(JsonNode node,String... expected){var names=new HashSet<String>();node.fieldNames().forEachRemaining(names::add);require(names.equals(Set.of(expected)),"LIB-POLICY-FIELDS");}
    static List<Pair> inspect(Path root)throws Exception{
        var policy=JSON.readTree(root.resolve("config/library-adoption.json").toFile());
        fields(policy,"schemaVersion","consumer","sourceOwner","artifacts","guides");
        require(policy.required("schemaVersion").isIntegralNumber()&&policy.required("schemaVersion").intValue()==1&&policy.required("consumer").asText().equals("atf_j5")&&policy.required("sourceOwner").asText().equals("atf_j0"),"LIB-OWNERSHIP");
        var provenance=JSON.readTree(root.resolve("library-provenance.json").toFile());
        require(provenance.required("consumer").asText().equals(policy.required("consumer").asText()),"LIB-CONSUMER");
        var adoption=provenance.required("consumerAdoption");require(adoption.required("consumer").asText().equals("atf_j5")&&adoption.required("upstreamQualificationInherited").isBoolean()&&!adoption.required("upstreamQualificationInherited").booleanValue(),"LIB-CONSUMER-QUALIFICATION");
        var properties=(Element)xml(Files.readAllBytes(root.resolve("pom.xml"))).getElementsByTagName("properties").item(0);
        require(policy.required("artifacts").isArray()&&policy.required("artifacts").size()==5,"LIB-ARTIFACTS");
        var pairs=new ArrayList<Pair>();var ids=new HashSet<String>();var expectedFiles=new HashSet<String>();
        for(var artifact:policy.required("artifacts")){
            fields(artifact,"name","marker");String name=artifact.required("name").asText(),marker=artifact.required("marker").asText();
            require(NAMES.contains(name)&&ids.add(name)&&marker.startsWith(name.equals("test-kit")?"atf.testkit.":"atf."+name+"."),"LIB-ARTIFACT");
            String version=child(properties,"atf."+(name.equals("test-kit")?"testkit":name)+".version");require(version.matches("[a-zA-Z0-9_.-]+")&&!version.contains(".."),"LIB-VERSION");
            String coordinate="atf.api:atf-"+name+":"+version;Path jar=root.resolve("lib/atf-"+name+"-"+version+".jar"),pom=root.resolve("lib/atf-"+name+"-"+version+".pom");
            expectedFiles.add(jar.getFileName().toString());expectedFiles.add(pom.getFileName().toString());
            require(!Files.exists(root.resolve("atf-"+name)),"LIB-SOURCE-SHADOW");require(Files.isRegularFile(jar)&&Files.isRegularFile(pom),"LIB-PAIR");
            JsonNode metadata=null;String jarHash,pomHash;
            if(name.equals("core")||name.equals("reporting")){
                for(var entry:provenance.required("libraries"))if(entry.required("artifact").asText().equals(coordinate)){require(metadata==null,"LIB-DUPLICATE-PROVENANCE");metadata=entry;}
                require(metadata!=null&&metadata.required("upstreamProducer").asText().equals("atf_j0"),"LIB-PROVENANCE");
                jarHash=metadata.required("files").required("jar").required("sha256").asText();pomHash=metadata.required("files").required("pom").required("sha256").asText();
                require(metadata.required("files").required("jar").required("path").asText().equals("lib/"+jar.getFileName())&&metadata.required("files").required("pom").required("path").asText().equals("lib/"+pom.getFileName()),"LIB-PROVENANCE-PATH");
            }else{
                metadata=provenance.required("platformLibraries").required(name);require(metadata.required("coordinate").asText().equals(coordinate),"LIB-COORDINATE");
                var ownership=provenance.required("platformLibraryOwnership");require(ownership.required("sourceOwner").asText().equals("atf_j0")&&ownership.required("feedbackOwner").asText().equals("atf_j0"),"LIB-OWNERSHIP");
                jarHash=metadata.required("binarySha256").asText();pomHash=metadata.required("publishedPomSha256").asText();
            }
            require(hash(jar).equalsIgnoreCase(jarHash),"LIB-JAR-HASH");require(hash(pom).equalsIgnoreCase(pomHash),"LIB-POM-HASH");
            var inputs=metadata.required("producerInputs");require(inputs.required("files").isIntegralNumber()&&inputs.required("files").intValue()>0&&inputs.required("sha256").asText().matches("(?i)[0-9a-f]{64}"),"LIB-INPUTS");
            require(inputs.required("nativeTests").isArray()&&!inputs.required("nativeTests").isEmpty(),"LIB-NATIVE-CATALOG");
            var nativePaths=new HashSet<String>();for(var test:inputs.required("nativeTests")){
                String path=test.required("path").asText();require(path.startsWith("src/test/java/")&&!path.contains("..")&&!path.contains("\\")&&nativePaths.add(path)&&test.required("sha256").asText().matches("(?i)[0-9a-f]{64}"),"LIB-NATIVE-CATALOG");
            }
            if(metadata.has("producerInputsSha256"))require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(inputs))).equalsIgnoreCase(metadata.required("producerInputsSha256").asText()),"LIB-INPUTS-DIGEST");
            var published=xml(Files.readAllBytes(pom));require(child(published,"groupId").equals("atf.api")&&child(published,"artifactId").equals("atf-"+name)&&child(published,"version").equals(version),"LIB-POM-COORDINATE");
            try(var archive=new JarFile(jar.toFile())){
                var embedded=archive.getJarEntry("META-INF/maven/atf.api/atf-"+name+"/pom.xml");require(embedded!=null,"LIB-EMBEDDED-POM");
                require(Arrays.equals(archive.getInputStream(embedded).readAllBytes(),Files.readAllBytes(pom)),"LIB-EMBEDDED-POM");
                require(archive.getJarEntry(marker.replace('.','/')+".class")!=null,"LIB-MARKER");
                for(var entry:archive.stream().toList()){
                    require(!entry.getName().endsWith(".java"),"LIB-EMBEDDED-SOURCE");
                    if(entry.getName().endsWith(".class"))require(entry.getName().startsWith((name.equals("test-kit")?"atf/testkit/":"atf/"+name+"/")),"LIB-FOREIGN-CLASS");
                }
            }
            pairs.add(new Pair(name,coordinate,jar,pom,jarHash,pomHash,marker));
        }
        var guides=policy.required("guides");require(guides.isArray()&&guides.size()==6,"LIB-GUIDES");for(var guide:guides){String name=guide.asText();require(name.matches("README(?:\\.(?:Core|Reporting|Console|Mock|TestKit))?\\.md")&&expectedFiles.add(name),"LIB-GUIDES");}
        try(var entries=Files.list(root.resolve("lib"))){var actual=entries.toList();require(actual.stream().allMatch(Files::isRegularFile)&&actual.stream().map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(expectedFiles),"LIB-FLAT-LAYOUT");}
        return List.copyOf(pairs);
    }
    static void loaded(Pair pair,Class<?> type)throws Exception{
        Path source=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        require(Files.isRegularFile(source)&&hash(source).equalsIgnoreCase(pair.jarHash()),"LIB-CODE-SOURCE");
    }
}

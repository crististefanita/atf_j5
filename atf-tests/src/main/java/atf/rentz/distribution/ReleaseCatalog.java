package atf.rentz.distribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Rentz delivery policy: preparation, verified local candidate and published asset have distinct gates. */
public final class ReleaseCatalog {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public record Edition(Path folder, JsonNode manifest, ApkDownloadTarget target, String state) { }
    public static Path root() {
        String configured=System.getProperty("rentz.project.root", "");
        Path path=Path.of(configured.isBlank()?System.getProperty("user.dir"):configured).toAbsolutePath().normalize();
        while(path!=null) {
            if(Files.isRegularFile(path.resolve("release/index.json")) && Files.isRegularFile(path.resolve("pom.xml")))return path;
            if(!configured.isBlank())break;
            path=path.getParent();
        }
        throw new IllegalStateException("RELEASE-ROOT: explicit Rentz project or complete source snapshot required");
    }
    public static Edition verify(Path root, boolean requireDelivery) throws IOException {
        JsonNode index=read(root.resolve("release/index.json"));
        String version=index.path("currentVersion").asText(),repository=index.path("repository").asText();
        require(repository.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]*/[A-Za-z0-9_-][A-Za-z0-9_.-]*"),"RELEASE-REPOSITORY");
        require(version.matches("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)"),"RELEASE-VERSION");
        require(index.path("schemaVersion").asInt()==1 && index.path("versions").isArray()
                && index.path("versions").size()==1 && index.path("versions").get(0).asText().equals(version),"RELEASE-INDEX");
        Path folder=root.resolve("release/"+version);JsonNode manifest=read(folder.resolve("release-validation.json"));
        require(!Files.isSymbolicLink(folder) && folder.toRealPath().startsWith(root.toRealPath()),"RELEASE-PHYSICAL-PATH");
        JsonNode identity=manifest.path("release");
        require(identity.path("versionName").asText().equals(version) && identity.path("applicationId").asText().equals("ro.rentzlab.app"),"RELEASE-IDENTITY");
        String gradle=Files.readString(root.resolve("rentz-lab-app/app/build.gradle"));
        require(gradle.contains("versionName '"+version+"'") && number(identity,"versionCode")==declared(gradle,"versionCode")
                && number(identity,"minSdk")==declared(gradle,"minSdk") && number(identity,"targetSdk")==declared(gradle,"targetSdk"),"RELEASE-GRADLE");
        String state=manifest.path("publication").path("state").asText();
        require(List.of("DRAFT","PREPARED","PUBLISHED").contains(state),"RELEASE-STATE");
        require(identity.path("apkName").asText().equals("RentzLab-"+version+".apk"),"RELEASE-FILENAME");
        for(String name:List.of("README.md","reports/README.md","release-notes.md"))
            require(Files.isRegularFile(folder.resolve(name)),"RELEASE-DOCUMENT: "+name);
        try(var children=Files.list(root.resolve("release"))) {
            require(children.filter(Files::isDirectory).map(p->p.getFileName().toString()).toList().equals(List.of(version)),"RELEASE-POPULATION");
        }
        require(Files.readString(root.resolve("release/README.md")).contains("("+version+"/README.md)"),"RELEASE-CATALOG-LINK");
        verifyMainGuide(Files.readString(root.resolve("README.md")),repository,version,state);
        for(String retired:List.of("dist","artifacts","releases"))require(!Files.exists(root.resolve(retired)),"RELEASE-RETIRED: "+retired);
        if(state.equals("DRAFT")) {
            require(!requireDelivery,"RELEASE-NOT-QUALIFIED");
            require(identity.path("apkBytes").isNull() && identity.path("sha256").isNull(),"RELEASE-DRAFT-IDENTITY");
            return new Edition(folder,manifest,null,state);
        }
        ApkDownloadTarget target=new ApkDownloadTarget(repository,version,number(identity,"apkBytes"),identity.path("sha256").asText());
        Path apk=folder.resolve(target.fileName());ApkPayload.verify(apk,target.bytes(),target.sha256());
        require(Files.readString(folder.resolve(target.fileName()+".sha256")).trim().equals(target.sha256()+"  "+target.fileName()),"RELEASE-CHECKSUM");
        JsonNode qualification=manifest.path("qualification");
        require(qualification.path("status").asText().equals("PASS") && number(qualification,"tests")>0
                && number(qualification,"failures")==0 && number(qualification,"errors")==0 && number(qualification,"skipped")==0,"RELEASE-QUALIFICATION");
        require(identity.path("debuggable").isBoolean() && !identity.path("debuggable").booleanValue()
                && identity.path("certificateSha256").asText().matches("[a-f0-9]{64}"),"RELEASE-SIGNING");
        verifyPayload(folder,manifest);
        if(state.equals("PUBLISHED"))require(number(manifest.path("publication"),"releaseId")>0
                && manifest.path("publication").path("sourceCommit").asText().matches("[a-f0-9]{40}"),"RELEASE-PUBLISHED-IDENTITY");
        return new Edition(folder,manifest,target,state);
    }
    public static void verifyMainGuide(String guide,String repository,String version,String state) throws IOException {
        String normalized=guide.replace("\r\n","\n");int start=normalized.indexOf("\n## Jocul\n");
        require(start>=0,"RELEASE-MAIN-SECTION");int end=normalized.indexOf("\n## ",start+1);
        String section=normalized.substring(start,end<0?normalized.length():end);
        var links=Pattern.compile("\\]\\((https://github\\.com/[^)]+/releases/[^)]+|release/[^)]+)\\)")
                .matcher(section).results().map(match->match.group(1)).toList();
        String local="release/"+version+"/";
        List<String> expected=switch(state) {
            case "DRAFT" -> List.of(local+"README.md");
            case "PREPARED" -> List.of(local+"README.md",local+"RentzLab-"+version+".apk");
            case "PUBLISHED" -> List.of("https://github.com/"+repository+"/releases/tag/v"+version,
                    "https://github.com/"+repository+"/releases/download/v"+version+"/RentzLab-"+version+".apk");
            default -> throw new IOException("RELEASE-STATE");
        };
        require(links.equals(expected),"RELEASE-MAIN-LINKS");
        require(normalized.contains("(release/README.md)"),"RELEASE-MAIN-CATALOG");
    }
    private static void verifyPayload(Path folder,JsonNode manifest) throws IOException {
        var expected=new HashSet<String>();
        for(JsonNode row:manifest.path("integrity").path("files")) {
            String path=ReleasePaths.relative(row.path("path").asText());
            require(expected.add(path) && !path.equals("release-validation.json"),"RELEASE-PAYLOAD-ENTRY");
            Path file=folder.resolve(path);
            require(Files.isRegularFile(file) && !Files.isSymbolicLink(file) && Files.size(file)==number(row,"bytes")
                    && ApkPayload.hash(file).equals(row.path("sha256").asText()),"RELEASE-PAYLOAD-HASH: "+path);
        }
        var actual=new HashSet<String>();
        try(var files=Files.walk(folder)) {
            for(Path file:files.filter(Files::isRegularFile).toList()) {
                String name=folder.relativize(file).toString().replace('\\','/');
                if(!name.equals("release-validation.json"))actual.add(name);
            }
        }
        require(!expected.isEmpty() && expected.equals(actual),"RELEASE-PAYLOAD-POPULATION");
    }
    public static JsonNode read(Path path) throws IOException { return JSON.readTree(path.toFile()); }
    public static long number(JsonNode value,String field) throws IOException {
        JsonNode node=value.path(field);require(node.isIntegralNumber() && node.canConvertToLong(),"RELEASE-NUMBER: "+field);return node.longValue();
    }
    private static long declared(String text,String name) throws IOException {
        var found=Pattern.compile("\\b"+name+"\\s+(\\d+)\\b").matcher(text);require(found.find(),"RELEASE-GRADLE-FIELD: "+name);return Long.parseLong(found.group(1));
    }
    private static void require(boolean valid,String code) throws IOException { if(!valid)throw new IOException(code); }
    private ReleaseCatalog(){ }
}

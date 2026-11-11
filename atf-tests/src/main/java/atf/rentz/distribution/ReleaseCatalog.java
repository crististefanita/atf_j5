package atf.rentz.distribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Rentz delivery policy: preparation, verified local candidate and published asset have distinct gates. */
public final class ReleaseCatalog {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern GAME_SECTION = Pattern.compile("(?ms)^## Jocul[ \\t]*(?:\\n|\\z)(.*?)(?=^## |\\z)");
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    private static final Pattern LINK_BULLET = Pattern.compile("- \\[[^\\[\\]]+\\]\\(([^()\\s]+)\\)");
    private static final Pattern RELEASE_LINK = Pattern.compile("/releases/(tag|download)/|\\.apk(?:[?#].*)?$",
            Pattern.CASE_INSENSITIVE);
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
        String buildVersion=version;long buildVersionCode=number(identity,"versionCode");
        if(!index.path("candidate").isMissingNode()){
            JsonNode candidate=index.path("candidate");buildVersion=candidate.path("version").asText();buildVersionCode=number(candidate,"versionCode");
            require(candidate.isObject()&&candidate.size()==2&&buildVersion.matches("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)"),"RELEASE-CANDIDATE-SCHEMA");
            require(isMinorOrMajorStep(version,buildVersion)&&buildVersionCode>number(identity,"versionCode"),"RELEASE-CANDIDATE-VERSION-POLICY");
        }
        String gradle=Files.readString(root.resolve("rentz-lab-app/app/build.gradle"));
        require(gradle.contains("versionName '"+buildVersion+"'") && buildVersionCode==declared(gradle,"versionCode")
                && number(identity,"minSdk")==declared(gradle,"minSdk") && number(identity,"targetSdk")==declared(gradle,"targetSdk"),"RELEASE-GRADLE");
        JsonNode publication=manifest.path("publication");
        String state=publication.path("state").asText();
        require(List.of("DRAFT","PREPARED","PUBLISHED").contains(state),"RELEASE-STATE");
        require(identity.path("apkName").asText().equals("RentzLab-"+version+".apk"),"RELEASE-FILENAME");
        for(String name:List.of("README.md","reports/README.md","release-notes.md"))
            require(Files.isRegularFile(folder.resolve(name)),"RELEASE-DOCUMENT: "+name);
        try(var children=Files.list(root.resolve("release"))) {
            require(children.filter(Files::isDirectory).map(p->p.getFileName().toString()).toList().equals(List.of(version)),"RELEASE-POPULATION");
        }
        require(Files.readString(root.resolve("release/README.md")).contains("("+version+"/README.md)"),"RELEASE-CATALOG-LINK");
        String guide=Files.readString(root.resolve("README.md"));
        String downloadRepository=repository;
        if(publication.path("channel").asText().equals("authenticated-only")) {
            require(state.equals("PUBLISHED") && !manifest.has("publicDownload"),"RELEASE-RESTRICTED-STATE");
            downloadRepository=verifyRestrictedReceipt(root,repository,version,identity,publication);
            verifyRestrictedGuide(guide,downloadRepository,version);
        } else {
            String publicUrl=publicDownloadUrl(manifest.path("publicDownload"),identity,version,state);
            verifyMainGuide(guide,repository,version,state,publicUrl);
        }
        for(String retired:List.of("dist","artifacts","releases"))require(!Files.exists(root.resolve(retired)),"RELEASE-RETIRED: "+retired);
        if(state.equals("DRAFT")) {
            require(!requireDelivery,"RELEASE-NOT-QUALIFIED");
            require(identity.path("apkBytes").isNull() && identity.path("sha256").isNull(),"RELEASE-DRAFT-IDENTITY");
            return new Edition(folder,manifest,null,state);
        }
        ApkDownloadTarget target=new ApkDownloadTarget(downloadRepository,version,number(identity,"apkBytes"),identity.path("sha256").asText());
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
    private static String verifyRestrictedReceipt(Path root,String repository,String version,JsonNode identity,
                                                   JsonNode publication) throws IOException {
        String name="restricted-"+version+".json";
        try(var children=Files.list(root.resolve("release"))) {
            require(children.map(p->p.getFileName().toString()).filter(n->n.startsWith("restricted-") && n.endsWith(".json"))
                    .toList().equals(List.of(name)),"RELEASE-RESTRICTED-POPULATION");
        }
        JsonNode proof=read(root.resolve("release").resolve(name));
        String distribution=proof.path("releaseRepository").asText();
        String tag="v"+version;
        require(distribution.matches(Pattern.quote(repository.split("/")[0])+"/[A-Za-z0-9_-][A-Za-z0-9_.-]*")
                && !distribution.equals(repository),"RELEASE-RESTRICTED-REPOSITORY");
        require(proof.path("state").asText().equals("PUBLISHED") && proof.path("provider").asText().equals("GitHub")
                && proof.path("channel").asText().equals("authenticated-only")
                && proof.path("authenticatedDownload").asText().equals("PASS")
                && proof.path("version").asText().equals(version) && proof.path("sourceTag").asText().equals(tag)
                && proof.path("sourceCommit").equals(publication.path("sourceCommit"))
                && proof.path("sourceCommit").equals(identity.path("sourceCommit"))
                && proof.path("applicationId").equals(identity.path("applicationId"))
                && proof.path("certificateSha256").equals(identity.path("certificateSha256"))
                && proof.path("apkSha256").equals(identity.path("sha256"))
                && number(proof,"apkBytes")==number(identity,"apkBytes")
                && number(proof,"versionCode")==number(identity,"versionCode")
                && number(proof,"releaseId")==number(publication,"releaseId") && number(proof,"assetId")>0
                && proof.path("link").asText().equals(new ApkDownloadTarget(distribution,version,
                        number(identity,"apkBytes"),identity.path("sha256").asText()).publicUrl()),
                "RELEASE-RESTRICTED-IDENTITY");
        return distribution;
    }

    public static void verifyRestrictedGuide(String guide,String repository,String version) throws IOException {
        verifyMainGuide(guide, repository, version, "PUBLISHED");
    }
    public static void verifyMainGuide(String guide,String repository,String version,String state) throws IOException {
        verifyMainGuide(guide, repository, version, state, null);
    }
    public static void verifyMainGuide(String guide,String repository,String version,String state,String publicUrl) throws IOException {
        String normalized = guide.replace("\r\n", "\n");
        var section = GAME_SECTION.matcher(normalized);
        require(section.find(), "RELEASE-MAIN-SECTION");
        String content = section.group(1);
        String outside = normalized.substring(0, section.start()) + normalized.substring(section.end());
        require(!section.find(), "RELEASE-MAIN-SECTION");
        String local="release/"+version+"/";
        List<String> expected=switch(state) {
            case "DRAFT" -> List.of(local+"README.md");
            case "PREPARED" -> List.of(local+"README.md",local+"RentzLab-"+version+".apk");
            case "PUBLISHED" -> List.of("https://github.com/"+repository+"/releases/tag/v"+version,
                    publicUrl != null ? publicUrl : "https://github.com/"+repository+"/releases/download/v"+version+"/RentzLab-"+version+".apk");
            default -> throw new IOException("RELEASE-STATE");
        };
        require(links(content).equals(expected), "RELEASE-MAIN-LINKS");
        var lines = content.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        require(lines.size() == expected.size() && lines.stream().allMatch(line -> LINK_BULLET.matcher(line).matches()),
                "RELEASE-MAIN-COPY");
        require(links(outside).stream().noneMatch(link -> RELEASE_LINK.matcher(link).find()), "RELEASE-MAIN-EXTRA-DOWNLOAD");
        require(links(outside).contains("release/README.md"), "RELEASE-MAIN-CATALOG");
    }
    private static List<String> links(String markdown) {
        return LINK.matcher(markdown).results().map(match -> match.group(1)).toList();
    }
    private static String publicDownloadUrl(JsonNode proof, JsonNode identity, String version, String state) throws IOException {
        if (proof.isMissingNode()) return null;
        require(proof.isObject() && state.equals("PUBLISHED"), "RELEASE-PUBLIC-DOWNLOAD-STATE");
        String value = proof.path("url").asText();
        URI uri;
        try {
            uri = URI.create(value);
            Instant.parse(proof.path("verifiedUtc").asText());
        } catch (IllegalArgumentException | DateTimeParseException malformed) {
            throw new IOException("RELEASE-PUBLIC-DOWNLOAD-METADATA", malformed);
        }
        require("https".equals(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null
                && uri.getPort() == -1 && uri.getRawQuery() == null && uri.getRawFragment() == null
                && ("/v" + version + "/" + identity.path("apkName").asText()).equals(uri.getRawPath()),
                "RELEASE-PUBLIC-DOWNLOAD-URL");
        require(number(proof,"httpStatus") == 200 && number(proof,"bytes") == number(identity,"apkBytes")
                && proof.path("sha256").asText().equals(identity.path("sha256").asText()),
                "RELEASE-PUBLIC-DOWNLOAD-IDENTITY");
        return value;
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
    static boolean isMinorOrMajorStep(String current,String candidate){
        String[] from=current.split("\\."),to=candidate.split("\\.");
        try{int major=Integer.parseInt(from[0]),minor=Integer.parseInt(from[1]);int nextMajor=Integer.parseInt(to[0]),nextMinor=Integer.parseInt(to[1]),patch=Integer.parseInt(to[2]);
            return patch==0&&((nextMajor==major&&nextMinor>minor)||(nextMajor==major+1&&nextMinor==0));
        }catch(RuntimeException invalid){return false;}
    }
    private static void require(boolean valid,String code) throws IOException { if(!valid)throw new IOException(code); }
    private ReleaseCatalog(){ }
}

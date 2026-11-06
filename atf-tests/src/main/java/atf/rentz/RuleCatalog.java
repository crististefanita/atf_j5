package atf.rentz;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import ro.rentzlab.core.*;
import ro.rentzlab.ai.*;

/** Audits provenance and live bindings; neither manifest nor partial observations become a runtime profile. */
public final class RuleCatalog {
    private RuleCatalog() { }
    private static final Map<String,Class<?>> IMPLEMENTATIONS=java.util.stream.Stream.of(
            Card.class,Contract.class,OpeningDeal.class,RentzMatch.class,RentzBoard.class,ScoreProfile.class,TrickRules.class,
            KnowledgeSampler.class,LabPolicy.class,PolicySettings.class,RentzPlayer.class,SearchPolicy.class,TacticalPolicy.class)
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Class::getSimpleName,type->type));
    public enum Certainty { PUBLISHER_SCREENSHOT_OBSERVED, UNCONFIRMED }
    public record Reading(Integer magnitude,Certainty certainty,String sourceId) { }
    public record Evidence(int players,String presetIdentity,Map<String,Reading> readings,List<Integer> unorderedFinishAwards) { }
    public record Claim(String id,String status,String source,String implementation,String verification) { }
    private static final Set<String> METRICS=Set.of("trickPenalty","queenPenalty","diamondPenalty","kingHeartsPenalty","tenClubsReward","firstFinishReward","whistTrickReward","otherFinishRewards");
    private static final Set<String> STATUSES=Set.of("PUBLISHER_DECLARED","PUBLISHER_APK_DECLARED","LAB_VARIANT_REQUIRES_REFERENCE_CONFIRMATION",
            "PUBLISHER_DECLARED_WITH_SETTLEMENT_INTERPRETATION","PUBLISHER_DECLARED_WITH_ELIGIBILITY_INTERPRETATION","PUBLISHER_DECLARED_WITH_OPENING_INTERPRETATION");

    public static Evidence evidence(Path file) throws IOException {
        var root=YamlDocuments.read(file);
        fields(root,Set.of("schemaVersion","role","referencePackage","presetIdentity","runtimeProfile","players","sources","readings","unorderedFourPlayerFinishAwards"));
        if(integer(root.required("schemaVersion"))!=1||!text(root,"role").equals("PARTIAL_REFERENCE_OBSERVATIONS")
                ||!text(root,"referencePackage").equals("com.boonwiz.rentz")||!text(root,"presetIdentity").equals("UNCONFIRMED")
                ||!root.required("runtimeProfile").isBoolean()||root.required("runtimeProfile").booleanValue())
            throw new IllegalArgumentException("Partial observations cannot claim a runtime or named preset");
        int players=integer(root.required("players"));if(players!=4)throw new IllegalArgumentException("Only the observed four-player table is covered");
        var sources=root.required("sources");if(!sources.isObject()||sources.isEmpty())throw new IllegalArgumentException("Source registry required");
        var sourceIds=new HashSet<String>();var sourceFields=sources.fields();
        while(sourceFields.hasNext()) {
            var source=sourceFields.next();
            if(!source.getValue().isTextual()||!source.getValue().textValue().startsWith("https://play-lh.googleusercontent.com/"))throw new IllegalArgumentException("Publisher screenshot URL required");
            sourceIds.add(source.getKey());
        }
        var readings=root.required("readings");fields(readings,METRICS);var values=new LinkedHashMap<String,Reading>();
        for(String metric:new TreeSet<>(METRICS)) {
            var node=readings.required(metric);fields(node,Set.of("magnitude","certainty","source"));
            var certainty=Certainty.valueOf(text(node,"certainty"));var number=node.required("magnitude");var source=node.required("source");
            Integer magnitude=null;String sourceId=null;
            if(certainty==Certainty.UNCONFIRMED) {
                if(!number.isNull()||!source.isNull())throw new IllegalArgumentException("Unknown value remains null, never an invented zero");
            } else {
                magnitude=integer(number);sourceId=text(node,"source");
                if(magnitude<0||!sourceIds.contains(sourceId))throw new IllegalArgumentException("Invalid observed magnitude or missing source");
            }
            values.put(metric,new Reading(magnitude,certainty,sourceId));
        }
        var awards=root.required("unorderedFourPlayerFinishAwards");if(!awards.isArray()||awards.size()!=players)throw new IllegalArgumentException("Observed award multiset required");
        var finishing=new ArrayList<Integer>();for(var award:awards){int value=integer(award);if(value<0)throw new IllegalArgumentException("Nonnegative award required");finishing.add(value);}
        return new Evidence(players,text(root,"presetIdentity"),Collections.unmodifiableMap(values),Card.immutable(finishing));
    }

    public static List<Claim> claims(Path file) throws IOException {
        var root=YamlDocuments.read(file);
        fields(root,Set.of("schemaVersion","domain","reference","manifestRole","claims","notImplemented"));
        if(integer(root.required("schemaVersion"))!=1||!text(root,"domain").equals("rentz")||!text(root,"manifestRole").equals("TRACEABILITY_NOT_RUNTIME_INTERPRETER"))throw new IllegalArgumentException("Rule manifest identity");
        var reference=root.required("reference");fields(reference,Set.of("package","officialPage","observedVersion","declaredMinimumAndroidApi","apkSha256","apkResourceKeys"));
        if(!text(reference,"package").equals("com.boonwiz.rentz")||!text(reference,"officialPage").equals("https://play.google.com/store/apps/details?id=com.boonwiz.rentz")
                ||text(reference,"observedVersion").isBlank()||integer(reference.required("declaredMinimumAndroidApi"))<1
                ||!text(reference,"apkSha256").matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Reference identity");
        var resourceKeys=new HashSet<String>();var resources=reference.required("apkResourceKeys");
        if(!resources.isArray()||resources.isEmpty())throw new IllegalArgumentException("APK resource registry required");
        for(var key:resources)if(!key.isTextual()||!key.textValue().matches("[a-z][a-z0-9_]+")||!resourceKeys.add(key.textValue()))throw new IllegalArgumentException("APK resource identity");
        var rows=root.required("claims");if(!rows.isArray()||rows.isEmpty())throw new IllegalArgumentException("Claims required");
        var ids=new HashSet<String>();var claims=new ArrayList<Claim>();
        for(var node:rows) {
            fields(node,Set.of("id","status","source","rule","implementation","verification"));
            String id=text(node,"id"),status=text(node,"status"),source=text(node,"source");
            if(!id.matches("[A-Z][A-Z0-9-]+")||!ids.add(id)||!STATUSES.contains(status)||text(node,"rule").isBlank())throw new IllegalArgumentException("Unknown status, duplicate id or empty rule");
            if(status.equals("PUBLISHER_APK_DECLARED")){
                if(!source.startsWith("APK_RESOURCE:")||!resourceKeys.contains(source.substring("APK_RESOURCE:".length())))throw new IllegalArgumentException("Missing APK resource anchor");
            }else if(!source.equals(status.startsWith("PUBLISHER_")?"PUBLISHER_DESCRIPTION":"LAB_CONTRACT"))throw new IllegalArgumentException("Status and provenance differ");
            claims.add(new Claim(id,status,source,text(node,"implementation"),text(node,"verification")));
        }
        var omitted=root.required("notImplemented");if(!omitted.isArray()||omitted.isEmpty())throw new IllegalArgumentException("Explicit scope exclusions required");
        for(var entry:omitted)if(!entry.isTextual()||entry.textValue().isBlank())throw new IllegalArgumentException("Scope exclusion required");
        return Card.immutable(claims);
    }

    public static void verifyBindings(Path project,List<Claim> claims) throws IOException {
        for(var claim:claims) {
            String[] implementation=claim.implementation().split("\\.",-1);
            if(implementation.length<1||implementation.length>2||!implementation[0].matches("[A-Z][A-Za-z0-9]+"))throw new IllegalArgumentException("Implementation binding");
            var type=IMPLEMENTATIONS.get(implementation[0]);
            if(type==null)throw new IllegalArgumentException("Missing class: "+claim.implementation());
            if(implementation.length==2&&Arrays.stream(type.getMethods()).noneMatch(m->m.getName().equals(implementation[1])))throw new IllegalArgumentException("Missing method: "+claim.implementation());
            String[] test=claim.verification().split("\\.",-1);
            if(test.length!=2||!test[0].matches("[A-Z][A-Za-z0-9]+Test")||!test[1].matches("[A-Za-z][A-Za-z0-9]+"))throw new IllegalArgumentException("Test binding");
            Path path=project.resolve("rentz-lab-app/game-core/src/test/java/ro/rentzlab/core/"+test[0]+".java");
            if(!Files.isRegularFile(path)||!Pattern.compile("\\bvoid\\s+"+Pattern.quote(test[1])+"\\s*\\(").matcher(Files.readString(path)).find())throw new IllegalArgumentException("Missing test declaration: "+claim.verification());
        }
    }

    private static String text(JsonNode node,String name){var value=node.required(name);if(!value.isTextual())throw new IllegalArgumentException("Text required: "+name);return value.textValue();}
    private static int integer(JsonNode node){if(!node.isIntegralNumber()||!node.canConvertToInt())throw new IllegalArgumentException("Integer required");return node.intValue();}
    private static void fields(JsonNode node,Set<String> expected){if(node==null||!node.isObject())throw new IllegalArgumentException("Object required");var actual=new HashSet<String>();node.fieldNames().forEachRemaining(actual::add);if(!actual.equals(expected))throw new IllegalArgumentException("Unexpected or missing fields: "+actual);}
}

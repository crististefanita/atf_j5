package atf.rentz;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Exposure belongs to the distribution namespace, never to a candidate or a renamed folder. */
public final class EvaluationRegistry {
    private static final ObjectMapper JSON=new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> ROOT_FIELDS=Set.of("schemaVersion","domain","generator","exposures");
    private static final Set<String> ENTRY_FIELDS=Set.of("players","seed","phase","batch","sourceIdentity","state");
    private EvaluationRegistry() { }
    public static void expose(Path registry,int players,List<Long> seeds,String phase,String batch,String sourceIdentity) throws IOException {
        if(players<3||players>6||seeds.isEmpty()||new HashSet<>(seeds).size()!=seeds.size()||seeds.stream().anyMatch(Objects::isNull)
                ||!Set.of("DEVELOPMENT","FRESH_EVALUATION").contains(phase)||batch.isBlank()||sourceIdentity.isBlank())throw new IllegalArgumentException("Exposure identity");
        registry=registry.toAbsolutePath().normalize();Files.createDirectories(registry.getParent());
        Path lockPath=registry.resolveSibling(registry.getFileName()+".lock");
        try(var channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE)) {
            FileLock lock;
            try{lock=channel.tryLock();}catch(OverlappingFileLockException error){throw new IllegalStateException("Registry is busy",error);}
            if(lock==null)throw new IllegalStateException("Registry is busy");
            try(lock) {
                ObjectNode root;
                if(Files.exists(registry)) {
                    var parsed=JSON.readTree(registry.toFile());
                    requireFields(parsed,ROOT_FIELDS);
                    if(!parsed.path("schemaVersion").isInt()||parsed.path("schemaVersion").intValue()!=1
                            ||!text(parsed,"domain").equals("rentz")||!text(parsed,"generator").equals("jvm-shuffle-eight-cards-v1")
                            ||!parsed.path("exposures").isArray())throw new IllegalArgumentException("Invalid exposure registry; do not reset it");
                    root=(ObjectNode)parsed;
                } else {
                    root=JSON.createObjectNode().put("schemaVersion",1).put("domain","rentz").put("generator","jvm-shuffle-eight-cards-v1");root.putArray("exposures");
                }
                var exposed=(ArrayNode)root.required("exposures");
                for(var prior:exposed){
                    requireFields(prior,ENTRY_FIELDS);
                    if(!prior.path("players").isInt()||prior.path("players").intValue()<3||prior.path("players").intValue()>6
                        ||!prior.path("seed").isIntegralNumber()||!prior.path("seed").canConvertToLong()||!Set.of("DEVELOPMENT","FRESH_EVALUATION").contains(prior.path("phase").asText())
                        ||text(prior,"batch").isBlank()||text(prior,"sourceIdentity").isBlank()
                        ||!text(prior,"state").equals("EXPOSED_BEFORE_EXECUTION"))throw new IllegalArgumentException("Corrupt exposure entry; preserve registry");
                }
                for(var prior:exposed)if(prior.path("players").asInt()==players&&seeds.contains(prior.path("seed").longValue())
                        &&(phase.equals("FRESH_EVALUATION")||prior.path("phase").asText().equals("FRESH_EVALUATION")))throw new IllegalArgumentException("Distribution already exposed: "+prior.path("seed"));
                for(long seed:seeds)exposed.addObject().put("players",players).put("seed",seed).put("phase",phase).put("batch",batch).put("sourceIdentity",sourceIdentity).put("state","EXPOSED_BEFORE_EXECUTION");
                Path temporary=Files.createTempFile(registry.getParent(),"rentz-exposure-",".tmp");JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(),root);
                Files.move(temporary,registry,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void requireFields(JsonNode node,Set<String> expected){
        if(node==null||!node.isObject()||node.size()!=expected.size())throw new IllegalArgumentException("Invalid registry shape; preserve registry");
        for(String field:expected)if(!node.has(field))throw new IllegalArgumentException("Missing registry field: "+field);
    }

    private static String text(JsonNode node,String field){
        var value=node.path(field);
        if(!value.isTextual())throw new IllegalArgumentException("Registry text required: "+field);
        return value.textValue();
    }
}

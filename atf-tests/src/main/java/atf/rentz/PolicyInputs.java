package atf.rentz;

import ro.rentzlab.ai.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import ro.rentzlab.core.*;

public final class PolicyInputs {
    private PolicyInputs() { }
    public static PolicySettings read(Path path,Path project) throws IOException {
        var root=YamlDocuments.read(path);
        fields(root,Set.of("schemaVersion","id","status","information","normal","hard","rules"));
        if(!root.required("schemaVersion").isInt()||root.required("schemaVersion").intValue()!=1
                ||!root.required("id").asText().equals("RENTZ_PUBLIC_LOOKAHEAD_V1")||!root.required("status").asText().equals("EXPERIMENTAL_CANDIDATE")
                ||!root.required("information").asText().equals("OWN_HAND_AND_PUBLIC_EVENTS"))throw new IllegalArgumentException("Policy identity or information boundary");
        var normal=root.required("normal");fields(normal,Set.of("choice","card"));
        if(!normal.required("choice").asText().equals("BASELINE_HAND_STRENGTH")||!normal.required("card").asText().equals("PUBLIC_TACTICAL"))throw new IllegalArgumentException("Normal policy binding");
        var hard=root.required("hard");fields(hard,Set.of("worlds","riskWeight","ownChainWeight","releaseWeight","skipThreatWeight"));
        if(!hard.required("worlds").isInt())throw new IllegalArgumentException("Integer world budget required");
        var settings=new PolicySettings(hard.required("worlds").intValue(),number(hard,"riskWeight"),number(hard,"ownChainWeight"),number(hard,"releaseWeight"),number(hard,"skipThreatWeight"));
        var rules=root.required("rules");if(!rules.isArray()||rules.isEmpty())throw new IllegalArgumentException("Code-bound strategy rules required");
        var ids=new HashSet<String>();var claims=new ArrayList<RuleCatalog.Claim>();
        for(var rule:rules){fields(rule,Set.of("id","condition","implementation","verification"));String id=rule.required("id").asText();if(!id.matches("[A-Z][A-Z_]+")||!ids.add(id)||!rule.required("condition").isTextual()||rule.required("condition").textValue().isBlank())throw new IllegalArgumentException("Rule identity or condition");claims.add(new RuleCatalog.Claim(id,"LAB_VARIANT_REQUIRES_REFERENCE_CONFIRMATION","LAB_CONTRACT",rule.required("implementation").asText(),rule.required("verification").asText()));}
        RuleCatalog.verifyBindings(project,claims);return settings;
    }
    private static double number(JsonNode root,String name){var value=root.required(name);if(!value.isNumber())throw new IllegalArgumentException("Numeric policy weight required");return value.doubleValue();}
    private static void fields(JsonNode root,Set<String> expected){if(root==null||!root.isObject())throw new IllegalArgumentException("Object required");var actual=new HashSet<String>();root.fieldNames().forEachRemaining(actual::add);if(!actual.equals(expected))throw new IllegalArgumentException("Unknown or missing policy fields: "+actual);}
}

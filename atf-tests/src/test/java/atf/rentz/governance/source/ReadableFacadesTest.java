package atf.rentz.governance.source;

import atf.core.files.AtfJavaSourceReferences;
import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class ReadableFacadesTest {
    private static final List<String> FACADES = List.of(
            "rentz-lab-app/game-core/src/main/java/ro/rentzlab/core/RentzMatch.java",
            "rentz-lab-app/game-core/src/main/java/ro/rentzlab/ai/search/DecisionEvaluator.java",
            "atf-tests/src/main/java/atf/rentz/OfflineArena.java");
    private static final Pattern CONTROL = Pattern.compile("\\b(?:if|for|while|switch|catch)\\s*\\(");

    @Test void publicFacadesExposeImportsAndOneControlStagePerLine() throws Exception {
        var findings = new ArrayList<String>();
        for (String file : FACADES) findings.addAll(inspect(file, Files.readString(GovernanceProject.root().resolve(file))));
        assertEquals(List.of(), findings, "GOV-READABILITY: scoped facades need explicit imports and readable control stages");
    }
    @Test void compressedControlAndWildcardImportFailWithoutRejectingTheHealthyNeighbor() {
        assertFalse(inspect("bad", "import java.util.*;\nif (ready) { for (int i=0;i<3;i++) use(i); }").isEmpty());
        assertEquals(List.of(), inspect("good", "import java.util.List;\nif (ready) {\n    for (int i=0;i<3;i++) {\n        use(i);\n    }\n}"));
        assertEquals(List.of(), inspect("literal", "String text=\"if(x) for(y)\"; // if(x) while(y)"));
    }
    static List<String> inspect(String name, String source) {
        var findings = new ArrayList<String>();
        var lines = AtfJavaSourceReferences.codeOnly(source).split("\\R");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].matches("\\s*import\\s+(?:static\\s+)?[\\w.]+\\*;.*")) findings.add(name + ":" + (i+1) + " wildcard import");
            if (CONTROL.matcher(lines[i]).results().count() > 1) findings.add(name + ":" + (i+1) + " compressed control flow");
        }
        return List.copyOf(findings);
    }
}

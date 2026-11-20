package atf.rentz.reporting;

import atf.rentz.governance.source.GovernanceProject;
import atf.testkit.AtfComponentTest;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks authored narrative only; localized UI data and opaque evidence are not translated. */
@AtfComponentTest
class ReportNarrativeGovernanceTest {
    private static final Pattern ROMANIAN_COPY=Pattern.compile("[ăâîșțĂÂÎȘȚ]|\\b(?:Pachet|Dispozitiv|Domeniu)\\b");
    private static final List<String> PRODUCERS=List.of(
            "atf-tests/src/main/java/atf/rentz/OfflineArenaReport.java",
            "atf-tests/src/main/java/atf/rentz/reporting/ComponentSuiteExtentReport.java",
            "atf-tests/src/main/java/atf/rentz/distribution/qualification/ReleaseQualification.java",
            "atf-tests/src/test/java/atf/rentz/RentzRulePublication.java",
            "atf-tests/src/test/java/atf/rentz/android/RentzNativeFlow.java",
            "atf-tests/src/test/java/atf/rentz/android/reporting/RentzUiIntegrationReport.java",
            "atf-tests/src/test/java/atf/rentz/android/reporting/RentzCollectionMotionReport.java",
            "atf-ui-browser/tests/atf/rentz/browser/reporting/RentzWebAcceptanceReport.java");

    @Test void allOwnedReportProducersKeepTheirAuthoredCopyInEnglish() throws Exception {
        var findings=new ArrayList<String>();
        var compiler=ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler,"Language governance runs on the documented JDK");
        try(var files=compiler.getStandardFileManager(null,null,java.nio.charset.StandardCharsets.UTF_8)){
            for(String relative:PRODUCERS){
                Path source=GovernanceProject.root().resolve(relative);
                var task=(JavacTask)compiler.getTask(null,files,null,List.of("-proc:none"),null,files.getJavaFileObjects(source));
                for(var unit:task.parse())new TreeScanner<Void,Void>(){
                    @Override public Void visitMethod(MethodTree method,Void unused){
                        if(relative.endsWith("/RentzNativeFlow.java")&&method.getName().contentEquals("decision"))return null;
                        return super.visitMethod(method,unused);
                    }
                    @Override public Void visitLiteral(LiteralTree literal,Void unused){
                        if(literal.getValue() instanceof String text&&ROMANIAN_COPY.matcher(text).find())findings.add(relative+": "+text);
                        return super.visitLiteral(literal,unused);
                    }
                }.scan(unit,null);
            }
        }
        assertEquals(List.of(),findings,"Authored report copy is English; localized data belongs to evidence, not headings");
    }
}

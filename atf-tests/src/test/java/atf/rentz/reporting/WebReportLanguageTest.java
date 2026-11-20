package atf.rentz.reporting;

import atf.rentz.governance.source.GovernanceProject;
import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.tools.ToolProvider;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual browser report adapter through the pinned Reporting JAR, without a browser. */
@AtfComponentTest
class WebReportLanguageTest {
    @ParameterizedTest @ValueSource(booleans={true,false})
    void legacyLocalizedTitleIsEvidenceAndVerdictIsNotChanged(boolean passed,@TempDir Path root) throws Exception {
        Path classes=Files.createDirectory(root.resolve("classes"));
        Path source=GovernanceProject.root().resolve("atf-ui-browser/tests/atf/rentz/browser/reporting/RentzWebAcceptanceReport.java");
        var compiler=ToolProvider.getSystemJavaCompiler();
        try(var files=compiler.getStandardFileManager(null,null,java.nio.charset.StandardCharsets.UTF_8)){
            assertTrue(compiler.getTask(null,files,null,List.of("-proc:none","--release","17","-cp",System.getProperty("java.class.path"),"-d",classes.toString()),null,files.getJavaFileObjects(source)).call());
        }
        Files.createDirectory(root.resolve("components"));
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",root.resolve("capture.png").toFile());
        byte[] png=Files.readAllBytes(root.resolve("capture.png"));
        String originalTitle="Complete contract 5: 5 · Regele de cupă";
        var json=new ObjectMapper();
        json.writeValue(root.resolve("checkpoints.json").toFile(),List.of(Map.of("title",originalTitle,"source","Recorded browser component fixture; no browser controlled","image","capture.png","snapshot","Jucător · Regele de cupă","actions",List.of("Click Închide"),"checks",List.of(Map.of("name","Original displayed contract","expected","Regele de cupă","actual","Regele de cupă","passed",passed)))));
        String original=Files.readString(root.resolve("checkpoints.json"));
        try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},getClass().getClassLoader())){
            Class.forName("atf.rentz.browser.reporting.RentzWebAcceptanceReport",true,loader).getMethod("main",String[].class).invoke(null,(Object)new String[]{root.toString()});
        }
        var document=Jsoup.parse(Files.readString(root.resolve("web-extent.html")));
        assertTrue(document.select(".card-header > .card-title > a.node > span").stream().anyMatch(e->e.text().equals("THEN Complete contract 5")),"The published step heading is exactly English, not a prefix of the localized title");
        assertTrue(document.text().toLowerCase(java.util.Locale.ROOT).contains("recorded checkpoint title"));
        assertTrue(document.text().contains(originalTitle),"The original title stays in evidence");
        assertTrue(document.text().contains("Regele de cupă"));
        var scenarios=document.selectFirst("#test-view .test-contents").selectFirst(".mt-4 > .accordion").children().stream().filter(e->e.hasClass("card")).toList();
        assertEquals(1,scenarios.size());
        assertEquals(passed?"pass":"fail",scenarios.getFirst().selectFirst(".card-header > .card-toolbar .badge.log").text().toLowerCase(java.util.Locale.ROOT),"Translation cannot turn a recorded failure into a pass");
        assertEquals(original,Files.readString(root.resolve("checkpoints.json")));
        assertArrayEquals(png,Files.readAllBytes(root.resolve("capture.png")));
    }
}

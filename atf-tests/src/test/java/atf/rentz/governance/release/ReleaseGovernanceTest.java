package atf.rentz.governance.release;

import atf.rentz.RentzRulePublication;
import atf.rentz.distribution.ReleaseCatalog;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.ComponentAuditExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static atf.testkit.assertions.AuditedAssertions.*;

/** A draft is inspectable but never a qualified delivery; current navigation stays unambiguous. */
@AtfComponentTest
class ReleaseGovernanceTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT=RentzRulePublication.scoped(ReleaseGovernanceTest.class,
            "release-governance.html","Current release policy; preparation is not Android acceptance");
    @TempDir Path temporary;
    @Test void currentReleaseMatchesGradleAndHasOneUnambiguousProductEntry() throws Exception {
        var edition=ReleaseCatalog.verify(ReleaseCatalog.root(),false);
        assertNotNull(edition,"Catalog verification returns an explicit edition");
        assertTrue(java.util.List.of("DRAFT","PREPARED","PUBLISHED").contains(edition.state()),"Actual release state remains explicit");
        if(edition.state().equals("DRAFT"))assertNull(edition.target(),"A draft cannot invent a downloadable APK");
        else assertNotNull(edition.target(),"A qualified edition has a complete expected identity");
    }
    @Test void personalPublishedRouteHasOnlyTheReleaseAndItsDirectAsset() throws Exception {
        assertDoesNotThrow(()->ReleaseCatalog.verifyMainGuide(guide("PUBLISHED"),"example/game","1.2.0","PUBLISHED"));
        assertDoesNotThrow(()->ReleaseCatalog.verifyMainGuide(guide("PREPARED"),"example/game","1.2.0","PREPARED"));
        assertDoesNotThrow(()->ReleaseCatalog.verifyMainGuide(guide("DRAFT"),"example/game","1.2.0","DRAFT"));
    }
    @ParameterizedTest @ValueSource(strings={"duplicate","folder","old","latest","raw","missing","reversed"})
    void staleOrRedundantDownloadRoutesAreRejected(String defect) throws Exception {
        String original=guide("PUBLISHED"),candidate=switch(defect) {
            case "duplicate" -> original.replace("\n## Documentație", "\n- [Again](https://github.com/example/game/releases/tag/v1.2.0)\n## Documentație");
            case "folder" -> original.replace("https://github.com/example/game/releases/tag/v1.2.0","release/1.2.0/README.md");
            case "old" -> original.replace("/v1.2.0/","/v1.1.0/");
            case "latest" -> original.replace("tag/v1.2.0","latest");
            case "raw" -> original.replace("github.com/example/game/releases/download/v1.2.0","raw.githubusercontent.com/example/game/master");
            case "missing" -> original.replace("- [APK](https://github.com/example/game/releases/download/v1.2.0/RentzLab-1.2.0.apk)","");
            case "reversed" -> original.replace("tag/v1.2.0","temporary").replace("download/v1.2.0/RentzLab-1.2.0.apk","tag/v1.2.0")
                    .replace("temporary","download/v1.2.0/RentzLab-1.2.0.apk");
            default -> throw new IllegalArgumentException(defect);
        };
        var failure=assertThrows(IOException.class,()->ReleaseCatalog.verifyMainGuide(candidate,"example/game","1.2.0","PUBLISHED"));
        assertEquals("RELEASE-MAIN-LINKS",failure.getMessage(),"The actual navigation defect is rejected: "+defect);
    }
    @Test void numericIdentityCannotBeTruncated() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var failure=assertThrows(IOException.class,()->ReleaseCatalog.number(json.readTree("{\"bytes\":12.75}"),"bytes"));
        assertEquals("RELEASE-NUMBER: bytes",failure.getMessage());
        assertEquals(803960L,ReleaseCatalog.number(json.readTree("{\"bytes\":803960}"),"bytes"),"Integral neighboring identity is unchanged");
    }
    @Test void aDraftPassesPreparationInspectionButCannotPassTheDeliveryGate() throws Exception {
        Path root=temporary.resolve("source snapshot with spaces"),folder=root.resolve("release/1.2.0");
        Files.createDirectories(folder.resolve("reports"));Files.createDirectories(root.resolve("rentz-lab-app/app"));
        Files.writeString(root.resolve("pom.xml"),"<project/>");Files.writeString(root.resolve("README.md"),guide("DRAFT"));
        Files.writeString(root.resolve("rentz-lab-app/app/build.gradle"),"versionName '1.2.0'; versionCode 3; minSdk 26; targetSdk 36");
        Files.writeString(root.resolve("release/index.json"),
                "{\"schemaVersion\":1,\"repository\":\"example/game\",\"currentVersion\":\"1.2.0\",\"versions\":[\"1.2.0\"]}");
        Files.writeString(root.resolve("release/README.md"),"[Release](1.2.0/README.md)");
        for(String file:java.util.List.of("README.md","reports/README.md","release-notes.md"))Files.writeString(folder.resolve(file),"# Explicit draft");
        Files.writeString(folder.resolve("release-validation.json"),"""
                {"release":{"versionName":"1.2.0","versionCode":3,"applicationId":"ro.rentzlab.app","minSdk":26,"targetSdk":36,
                  "apkName":"RentzLab-1.2.0.apk","apkBytes":null,"sha256":null},"publication":{"state":"DRAFT"}}
                """);
        assertEquals("DRAFT",ReleaseCatalog.verify(root,false).state(),"Preparation remains inspectable without a fabricated binary");
        var failure=assertThrows(IOException.class,()->ReleaseCatalog.verify(root,true));
        assertEquals("RELEASE-NOT-QUALIFIED",failure.getMessage(),"Publication cannot promote a draft by an operator's label");
    }
    @Test void metadataCannotHideDuplicatePropertiesOrTrailingDocuments() throws Exception {
        Path file=temporary.resolve("metadata.json");
        Files.writeString(file,"{\"state\":\"DRAFT\",\"state\":\"PUBLISHED\"}");
        assertThrows(IOException.class,()->ReleaseCatalog.read(file),"Duplicate state cannot overwrite the original declaration");
        Files.writeString(file,"{} {}");
        assertThrows(IOException.class,()->ReleaseCatalog.read(file),"A trailing document cannot be silently ignored");
    }
    @Test void theLiveComparisonOracleCannotAcceptAChangedOrMissingField() {
        var target=new atf.rentz.distribution.ApkDownloadTarget("example/game","1.2.0",17,"a".repeat(64));
        var healthy=new atf.rentz.distribution.GitHubApkDownload.Proof("1.2.0",target.publicUrl(),17,target.sha256(),"PASS",200,temporary.resolve("witness"));
        var accepted=atf.rentz.distribution.ReleaseDownloadAcceptance.comparisons(target,healthy);
        assertEquals(6,accepted.size(),"The complete declared comparison population is mandatory");
        assertTrue(accepted.stream().allMatch(atf.reporting.contract.AtfAssertion::passed),"Healthy neighbor is accepted");
        for(int defect=0;defect<6;defect++) {
            var changed=new atf.rentz.distribution.GitHubApkDownload.Proof(defect==0?"1.1.0":"1.2.0",defect==1?"https://invalid.test":target.publicUrl(),
                    defect==2?16:17,defect==3?"b".repeat(64):target.sha256(),defect==4?"NOT_CHECKED":"PASS",defect==5?404:200,healthy.file());
            assertFalse(atf.rentz.distribution.ReleaseDownloadAcceptance.comparisons(target,changed).stream()
                    .allMatch(atf.reporting.contract.AtfAssertion::passed),"Independent changed field is rejected: "+defect);
        }
        assertThrows(NullPointerException.class,()->atf.rentz.distribution.ReleaseDownloadAcceptance.comparisons(target,null),"No proof means no implicit PASS");
    }
    private static String guide(String state) {
        String local="release/1.2.0/";
        String entry=state.equals("PUBLISHED")?"https://github.com/example/game/releases/tag/v1.2.0":local+"README.md";
        String apk=state.equals("PUBLISHED")?"https://github.com/example/game/releases/download/v1.2.0/RentzLab-1.2.0.apk":local+"RentzLab-1.2.0.apk";
        return "# Rentz\n\n## Jocul\n\n- [Release]("+entry+")\n"+(state.equals("DRAFT")?"":"- [APK]("+apk+")\n")
                +"\n## Documentație\n\n[Catalog](release/README.md)\n";
    }
}

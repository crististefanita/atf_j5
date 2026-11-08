package atf.rentz.distribution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/** Resolves the exact standalone ATF reporting artifact selected by the root Maven build. */
public record AtfReportingLibrary(String version, String artifactName, Path jar, String sha256) {
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+-j\\d+");

    public static AtfReportingLibrary resolve(Path projectRoot) throws IOException {
        Path root = projectRoot.toRealPath();
        Path pom = root.resolve("pom.xml").normalize();
        assertInsidePhysicalRoot(root, pom);
        if (!Files.isRegularFile(pom, LinkOption.NOFOLLOW_LINKS) || Files.size(pom) > 1_048_576)
            throw new IOException("RAG-REPORTING-POM");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(pom.toFile());
            NodeList nodes = document.getElementsByTagNameNS("*", "atf.reporting.version");
            if (nodes.getLength() != 1) throw new IOException("RAG-REPORTING-VERSION-COUNT");
            String version = nodes.item(0).getTextContent().trim();
            if (!VERSION.matcher(version).matches()) throw new IOException("RAG-REPORTING-VERSION-FORMAT");
            String name = "atf-reporting-" + version;
            Path jar = root.resolve("lib").resolve(name + ".jar").normalize();
            assertInsidePhysicalRoot(root, jar);
            if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || Files.size(jar) == 0)
                throw new IOException("RAG-REPORTING-JAR");
            return new AtfReportingLibrary(version, name, jar, sha256(Files.readAllBytes(jar)));
        } catch (IOException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new IOException("RAG-REPORTING-POM", invalid);
        }
    }

    private static void assertInsidePhysicalRoot(Path root, Path path) throws IOException {
        if (!path.normalize().startsWith(root) || Files.isSymbolicLink(path))
            throw new IOException("RAG-REPORTING-PATH");
        Path cursor = root;
        for (Path part : root.relativize(path.normalize())) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) throw new IOException("RAG-REPORTING-SYMLINK");
        }
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("RAG-REPORTING-SHA256", impossible);
        }
    }
}

package atf.rentz.discovery;

import com.sun.source.tree.Tree;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared source-level layer and executable inventory; the engine remains an independent oracle. */
public final class TestLayers {
    static final Set<String> LAYERS = Set.of("atf.testkit.AtfUnitTest", "atf.testkit.AtfComponentTest",
            "atf.testkit.AtfIntegrationTest", "atf.testkit.AtfE2eTest",
            "ro.rentzlab.testing.RentzComponentLayer", "ro.rentzlab.app.testing.AndroidComponentLayer");
    public record ReportSuite(String binaryName, boolean hasCases) { }
    public record Inspection(String name, boolean executable, List<String> layers, List<ReportSuite> suites) { }
    private TestLayers() { }
    public static List<Inspection> inspect(String source, Map<String, String> annotations) {
        return new TestHierarchy(List.of(source), annotations, Set.of()).inspect(0, false);
    }
    public static Map<String, String> annotations(List<String> sources) {
        var result = new HashMap<String, String>();
        try (var parser = JavaTestDeclarations.open()) {
            for (String source : sources) {
                var unit = parser.parse(source);
                for (var type : unit.types()) collect(type, unit, unit.packageName(), result);
            }
        }
        return Map.copyOf(result);
    }
    private static void collect(JavaTestDeclarations.Type type, JavaTestDeclarations.Unit unit,
                                String owner, Map<String, String> annotations) {
        String name = owner.isEmpty() ? type.name() : owner + "." + type.name();
        if (type.kind() == Tree.Kind.ANNOTATION_TYPE) {
            String enclosing = owner.equals(unit.packageName()) ? "" : "import " + owner + ".*;\n";
            if (annotations.putIfAbsent(name, unit.header() + enclosing + type.annotationSource()) != null) {
                throw new IllegalArgumentException("GOV-ANNOTATION-DUPLICATE: " + name);
            }
        }
        for (var nested : type.nested()) collect(nested, unit, name, annotations);
    }
}

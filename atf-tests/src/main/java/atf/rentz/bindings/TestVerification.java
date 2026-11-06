package atf.rentz.bindings;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/** Source eligibility only: compilation, JUnit discovery and execution remain separate gates. */
public final class TestVerification {
    public record Reference(String rule, Path source, String owner, String method) { }

    private TestVerification() { }

    public static void requireAll(List<Reference> references) throws IOException {
        if (references.isEmpty()) return;
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK compiler required for test declaration bindings");
        try (var files = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            var units = new HashMap<Path, CompilationUnitTree>();
            for (var reference : references) {
                Path source = reference.source().toAbsolutePath().normalize();
                if (!Files.isRegularFile(source)) throw rejected(reference, "source file is missing");
                var unit = units.get(source);
                if (unit == null) {
                    var diagnostics = new DiagnosticCollector<JavaFileObject>();
                    var task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null,
                            files.getJavaFileObjects(source));
                    unit = task.parse().iterator().next();
                    var error = diagnostics.getDiagnostics().stream()
                            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).findFirst();
                    if (error.isPresent()) throw rejected(reference, "invalid Java syntax at line " + error.get().getLineNumber());
                    units.put(source, unit);
                }
                require(unit, reference);
            }
        }
    }

    private static void require(CompilationUnitTree unit, Reference reference) {
        int separator = reference.owner().lastIndexOf('.');
        String packageName = reference.owner().substring(0, separator);
        String simpleName = reference.owner().substring(separator + 1);
        if (unit.getPackageName() == null || !unit.getPackageName().toString().equals(packageName))
            throw rejected(reference, "package differs from the bound owner");
        var owners = unit.getTypeDecls().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast)
                .filter(type -> type.getSimpleName().contentEquals(simpleName)).toList();
        if (owners.size() != 1) throw rejected(reference, "exact top-level owner is missing or ambiguous");
        var owner = owners.getFirst();
        if (owner.getKind() != com.sun.source.tree.Tree.Kind.CLASS || owner.getModifiers().getFlags().contains(Modifier.ABSTRACT))
            throw rejected(reference, "owner must be a concrete test class");
        var methods = owner.getMembers().stream().filter(MethodTree.class::isInstance).map(MethodTree.class::cast)
                .filter(method -> method.getName().contentEquals(reference.method())).toList();
        if (methods.size() != 1) throw rejected(reference, "direct method is missing or overloaded");
        var method = methods.getFirst();
        var flags = method.getModifiers().getFlags();
        if (flags.contains(Modifier.PRIVATE) || flags.contains(Modifier.STATIC) || flags.contains(Modifier.ABSTRACT)
                || method.getBody() == null || method.getReturnType() == null || !method.getReturnType().toString().equals("void"))
            throw rejected(reference, "test method must be an instance void method with a body");
        boolean marked = method.getModifiers().getAnnotations().stream()
                .anyMatch(annotation -> JupiterMethodMarkers.recognizes(unit, owner, annotation.getAnnotationType().toString()));
        if (!marked) throw rejected(reference, "unambiguous Jupiter @Test or @ParameterizedTest declaration required");
    }

    private static IllegalArgumentException rejected(Reference reference, String reason) {
        return new IllegalArgumentException("Rule " + reference.rule() + ": " + reference.source() + "#"
                + reference.method() + " is not an eligible test declaration: " + reason);
    }
}

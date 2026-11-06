package atf.rentz.bindings;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import java.util.List;
import java.util.Set;

/** Resolves the closed declaration vocabulary without treating foreign or shadowed names as Jupiter. */
final class JupiterMethodMarkers {
    private static final Set<String> SUPPORTED = Set.of(
            "org.junit.jupiter.api.Test", "org.junit.jupiter.params.ParameterizedTest");

    private JupiterMethodMarkers() { }

    static boolean recognizes(CompilationUnitTree unit, ClassTree owner, String name) {
        String first = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        if (shadows(unit, owner, first)) return false;
        if (name.contains(".")) {
            boolean importedRoot = unit.getImports().stream()
                    .anyMatch(entry -> entry.getQualifiedIdentifier().toString().endsWith("." + first));
            return !importedRoot && SUPPORTED.contains(name);
        }
        if (unit.getImports().stream().anyMatch(entry -> entry.isStatic()
                && entry.getQualifiedIdentifier().toString().endsWith("." + name))) return false;
        List<String> imports = unit.getImports().stream().filter(entry -> !entry.isStatic())
                .map(entry -> entry.getQualifiedIdentifier().toString()).toList();
        var explicit = imports.stream().filter(entry -> entry.endsWith("." + name)).distinct().toList();
        if (!explicit.isEmpty()) return explicit.size() == 1 && SUPPORTED.contains(explicit.getFirst());
        boolean unknownStaticWildcard = unit.getImports().stream().filter(entry -> entry.isStatic())
                .map(entry -> entry.getQualifiedIdentifier().toString())
                .anyMatch(entry -> entry.endsWith(".*") && !entry.equals("org.junit.jupiter.api.Assertions.*"));
        if (unknownStaticWildcard) return false;
        var wildcards = imports.stream().filter(entry -> entry.endsWith(".*"))
                .map(entry -> entry.substring(0, entry.length() - 2)).distinct().toList();
        var candidates = wildcards.stream().filter(pkg -> SUPPORTED.contains(pkg + "." + name)).toList();
        return candidates.size() == 1 && wildcards.stream()
                .allMatch(pkg -> pkg.equals(candidates.getFirst()) || pkg.startsWith("java."));
    }

    private static boolean shadows(CompilationUnitTree unit, ClassTree owner, String name) {
        return unit.getTypeDecls().stream().anyMatch(tree -> tree instanceof ClassTree type && type.getSimpleName().contentEquals(name))
                || owner.getMembers().stream().anyMatch(tree -> tree instanceof ClassTree type && type.getSimpleName().contentEquals(name));
    }
}

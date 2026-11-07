package atf.rentz.discovery;

import com.sun.source.tree.Tree;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;

/** Java override identity with generic substitution; class declarations precede default methods. */
final class InheritedMethods {
    record Resolved(TestHierarchy.Declaration owner, JavaTestDeclarations.Method method, String signature) {
        boolean runnable(AnnotationGraph annotations) {
            return method.body() && !method.modifiers().contains(Modifier.PRIVATE)
                    && !method.modifiers().contains(Modifier.STATIC)
                    && !annotations.resolve(method.annotations(), owner.unit(), AnnotationGraph.METHODS).isEmpty();
        }
    }
    static Map<String, Resolved> effective(TestHierarchy graph, TestHierarchy.Declaration type, boolean strict) {
        var variables = new LinkedHashMap<String, String>();
        type.type().variables().forEach((name, bound) -> variables.put(name, graph.symbol(bound, type, variables)));
        return effective(graph, type, variables, strict, new HashSet<>());
    }
    private static Map<String, Resolved> effective(TestHierarchy graph, TestHierarchy.Declaration type,
                                                   Map<String, String> variables, boolean strict, Set<String> visiting) {
        if (!visiting.add(type.name())) throw new IllegalArgumentException("TEST-INVENTORY: cyclic type hierarchy");
        try {
            var result = new LinkedHashMap<String, Resolved>();
            for (var method : type.type().methods()) {
                var parameters = new LinkedHashMap<>(variables);
                method.variables().forEach((name, bound) -> parameters.put(name, graph.symbol(bound, type, parameters)));
                String key = method.name() + "(" + String.join(",", method.parameters().stream()
                        .map(p -> graph.symbol(p, type, parameters)).toList()) + ")";
                result.put(type.name() + "#" + key, new Resolved(type, method, key));
            }
            var parents = new java.util.ArrayList<>(graph.parents(type, variables, strict));
            parents.sort(java.util.Comparator.comparing(p -> p.declaration().type().kind() == Tree.Kind.INTERFACE));
            for (var parent : parents) {
                for (var entry : effective(graph, parent.declaration(), parent.variables(), strict, visiting).entrySet()) {
                    var inherited = entry.getValue();
                    // Jupiter discovers package-private ancestor tests even across packages.
                    // A same-name method in another package does not override that declaration.
                    boolean hidden = result.values().stream().anyMatch(child -> overrides(child, inherited));
                    if (!inherited.method().modifiers().contains(Modifier.PRIVATE) && !hidden)
                        result.putIfAbsent(entry.getKey(), inherited);
                }
            }
            return result;
        } finally { visiting.remove(type.name()); }
    }
    private static boolean overrides(Resolved child, Resolved parent) {
        if (!child.signature().equals(parent.signature()) || child.method().modifiers().contains(Modifier.PRIVATE)) return false;
        if (parent.owner().type().kind() == Tree.Kind.INTERFACE) {
            return child.owner().type().kind() == Tree.Kind.INTERFACE || child.method().modifiers().contains(Modifier.PUBLIC);
        }
        var flags = parent.method().modifiers();
        return flags.contains(Modifier.PUBLIC) || flags.contains(Modifier.PROTECTED)
                || parent.owner().unit().packageName().equals(child.owner().unit().packageName());
    }
}

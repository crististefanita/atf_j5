package atf.rentz.discovery;

import com.sun.source.tree.Tree;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;

/** Source-only type graph. Unavailable ancestry is never silently treated as a test-free helper. */
final class TestHierarchy {
    record Declaration(String name, String binaryName, JavaTestDeclarations.Unit unit, JavaTestDeclarations.Type type) { }
    record Parent(Declaration declaration, Map<String, String> variables) { }
    private final Map<String, Declaration> types = new LinkedHashMap<>();
    private final List<JavaTestDeclarations.Unit> units;
    private final Set<String> approved;
    final AnnotationGraph annotations;

    TestHierarchy(List<String> sources, Map<String, String> metadata, Set<String> approved) {
        this.approved = Set.copyOf(approved);
        units = sources.stream().map(JavaTestDeclarations::parse).toList();
        for (var unit : units) {
            String prefix = unit.packageName().isEmpty() ? "" : unit.packageName() + ".";
            for (var type : unit.types()) collect(type, unit, prefix, prefix);
        }
        var graph = new HashMap<>(TestLayers.annotations(sources));
        metadata.forEach(graph::putIfAbsent);
        annotations = new AnnotationGraph(graph);
    }
    private void collect(JavaTestDeclarations.Type type, JavaTestDeclarations.Unit unit, String prefix, String binaryPrefix) {
        String name = prefix + type.name();
        String binaryName = binaryPrefix + type.name();
        if (types.putIfAbsent(name, new Declaration(name, binaryName, unit, type)) != null)
            throw new IllegalArgumentException("TEST-INVENTORY: duplicate type " + name);
        type.nested().forEach(child -> collect(child, unit, name + ".", binaryName + "$"));
    }
    List<TestLayers.Inspection> inspect(int source, boolean strict) {
        var result = new ArrayList<TestLayers.Inspection>();
        var unit = units.get(source);
        for (var type : unit.types()) {
            String name = unit.packageName().isEmpty() ? type.name() : unit.packageName() + "." + type.name();
            inspect(types.get(name), type.name(), List.of(), strict, result);
        }
        return List.copyOf(result);
    }
    private void inspect(Declaration declaration, String display, List<String> enclosing, boolean strict,
                         List<TestLayers.Inspection> result) {
        var layers = layers(declaration, new HashSet<>(), strict);
        if (layers.isEmpty()) layers = enclosing;
        var suites = execution(declaration, strict, new HashSet<>());
        result.add(new TestLayers.Inspection(display, !suites.isEmpty(), layers, suites));
        for (var nested : declaredNested(declaration)) {
            inspect(nested, display + "." + nested.type().name(), layers, strict, result);
        }
    }
    private List<TestLayers.ReportSuite> execution(Declaration d, boolean strict, Set<String> visiting) {
        if (!visiting.add(d.name())) throw new IllegalArgumentException("TEST-INVENTORY: cyclic type hierarchy");
        try {
            var methods = InheritedMethods.effective(this, d, strict);
            if (!d.type().concrete()) return List.of();
            boolean hasCases = annotated(d.type().annotations(), d.unit(), "org.junit.platform.suite.api.Suite")
                    || methods.values().stream().anyMatch(m -> m.runnable(annotations));
            var result = new ArrayList<TestLayers.ReportSuite>();
            var ancestors = new HashSet<String>();
            for (Declaration owner = d; owner != null; owner = superclass(owner, strict)) {
                if (!ancestors.add(owner.name())) throw new IllegalArgumentException("TEST-INVENTORY: cyclic type hierarchy");
                for (var nested : declaredNested(owner)) result.addAll(execution(nested, strict, visiting));
            }
            if (hasCases || !result.isEmpty()) result.addFirst(new TestLayers.ReportSuite(d.binaryName(), hasCases));
            return List.copyOf(result);
        } finally { visiting.remove(d.name()); }
    }
    private List<Declaration> declaredNested(Declaration owner) {
        return owner.type().nested().stream()
                .filter(type -> type.kind() == Tree.Kind.CLASS && !type.modifiers().contains(Modifier.STATIC)
                        && annotated(type.annotations(), owner.unit(), "org.junit.jupiter.api.Nested"))
                .map(type -> types.get(owner.name() + "." + type.name()))
                .toList();
    }
    private Declaration superclass(Declaration d, boolean strict) {
        return parents(d, Map.of(), strict).stream().map(Parent::declaration)
                .filter(parent -> parent.type().kind() == Tree.Kind.CLASS).findFirst().orElse(null);
    }
    private List<String> layers(Declaration d, Set<String> visiting, boolean strict) {
        if (!visiting.add(d.name())) throw new IllegalArgumentException("TEST-INVENTORY: cyclic type hierarchy");
        try {
            var result = new HashSet<>(annotations.resolve(d.type().annotations(), d.unit(), TestLayers.LAYERS));
            // Direct Kit terminals are not @Inherited; composed annotations can opt into inheritance.
            for (var parent : parents(d, Map.of(), strict)) {
                if (parent.declaration().type().kind() == Tree.Kind.INTERFACE)
                    result.addAll(layers(parent.declaration(), visiting, strict));
                else result.addAll(inheritedLayers(parent.declaration(), visiting, strict));
            }
            return result.stream().sorted().toList();
        } finally { visiting.remove(d.name()); }
    }
    private List<String> inheritedLayers(Declaration d, Set<String> visiting, boolean strict) {
        if (!visiting.add(d.name())) throw new IllegalArgumentException("TEST-INVENTORY: cyclic type hierarchy");
        try {
            var result = new HashSet<>(annotations.inheritedLayers(d.type().annotations(), d.unit()));
            for (var parent : parents(d, Map.of(), strict))
                if (parent.declaration().type().kind() != Tree.Kind.INTERFACE)
                    result.addAll(inheritedLayers(parent.declaration(), visiting, strict));
            return result.stream().sorted().toList();
        } finally { visiting.remove(d.name()); }
    }
    boolean annotated(List<String> values, JavaTestDeclarations.Unit unit, String terminal) {
        return !annotations.resolve(values, unit, Set.of(terminal)).isEmpty();
    }
    List<Parent> parents(Declaration d, Map<String, String> variables, boolean strict) {
        var result = new ArrayList<Parent>();
        for (String expression : d.type().parents()) {
            String raw = expression.replaceAll("<.*>", "").trim();
            String name = symbol(raw, d, variables);
            var parent = types.get(name);
            if (parent == null) {
                if (strict && !approved.contains(name) && !platform(name))
                    throw new IllegalArgumentException("TEST-INVENTORY: unresolved external base " + name + " of " + d.name());
                continue;
            }
            var bindings = new LinkedHashMap<String, String>();
            List<String> arguments = arguments(expression);
            int index = 0;
            for (var parameter : parent.type().variables().entrySet()) {
                String value = index < arguments.size() ? arguments.get(index) : parameter.getValue();
                bindings.put(parameter.getKey(), symbol(value, index < arguments.size() ? d : parent, variables));
                index++;
            }
            result.add(new Parent(parent, Map.copyOf(bindings)));
        }
        return result;
    }
    String symbol(String expression, Declaration context, Map<String, String> variables) {
        String name = expression.replaceAll("<.*>", "").trim().replace("...", "[]");
        if (name.endsWith("[]")) return symbol(name.substring(0, name.length()-2), context, variables) + "[]";
        if (variables.containsKey(name)) return variables.get(name);
        if (Set.of("boolean","byte","short","int","long","char","float","double","void").contains(name)) return name;
        String enclosing = context.name();
        while (enclosing.contains(".")) {
            enclosing = enclosing.substring(0, enclosing.lastIndexOf('.'));
            if (enclosing.equals(context.unit().packageName())) break;
            if (types.containsKey(enclosing + "." + name)) return enclosing + "." + name;
        }
        var known = new HashMap<String, String>();
        types.keySet().forEach(key -> known.put(key, ""));
        approved.forEach(key -> known.put(key, ""));
        String qualified = context.unit().qualified(name, known);
        if (types.containsKey(qualified) || approved.contains(qualified) || name.contains(".")
                || context.unit().imports().containsKey(name)) return qualified;
        if (platform("java.lang." + name)) return "java.lang." + name;
        for (String wildcard : context.unit().wildcards())
            if (platform(wildcard + "." + name)) return wildcard + "." + name;
        return qualified;
    }
    private static boolean platform(String name) {
        String binary = name;
        while (true) {
            try { Class.forName(binary, false, ClassLoader.getPlatformClassLoader()); return true; }
            catch (ClassNotFoundException missing) {
                int split = binary.lastIndexOf('.');
                if (split < 0) return false;
                binary = binary.substring(0, split) + "$" + binary.substring(split + 1);
            }
        }
    }
    private static List<String> arguments(String expression) {
        int start = expression.indexOf('<');
        if (start < 0) return List.of();
        var result = new ArrayList<String>();
        int depth = 0, from = start + 1;
        for (int i = from; i < expression.length(); i++) {
            char value = expression.charAt(i);
            if (value == '<') depth++;
            if (value == ',' && depth == 0 || value == '>' && depth == 0) {
                result.add(expression.substring(from, i).trim()); from = i + 1;
                if (value == '>') break;
            }
            if (value == '>') depth--;
        }
        return result;
    }
}

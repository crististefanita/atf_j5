package atf.rentz.discovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Qualified annotation composition shared by declared and inherited test contracts. */
final class AnnotationGraph {
    static final Set<String> METHODS = Set.of("org.junit.Test", "org.junit.jupiter.api.Test",
            "org.junit.jupiter.params.ParameterizedTest", "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.api.TestFactory", "org.junit.jupiter.api.TestTemplate");
    private final Map<String, String> known;
    AnnotationGraph(Map<String, String> sources) {
        known = new HashMap<>(sources);
        var terminals = new HashSet<>(METHODS);
        terminals.addAll(TestLayers.LAYERS);
        terminals.addAll(Set.of("org.junit.jupiter.api.Nested", "org.junit.platform.suite.api.Suite"));
        terminals.forEach(name -> known.putIfAbsent(name, ""));
    }
    List<String> resolve(List<String> names, JavaTestDeclarations.Unit unit, Set<String> terminals) {
        if (names.isEmpty()) return List.of();
        try (var parser = JavaTestDeclarations.open()) {
            return resolve(names, unit, terminals, new HashSet<>(), parser).stream().distinct().sorted().toList();
        }
    }
    List<String> inheritedLayers(List<String> names, JavaTestDeclarations.Unit unit) {
        if (names.isEmpty()) return List.of();
        var inherited = new ArrayList<String>();
        var symbols = new HashMap<>(known);
        symbols.put("java.lang.annotation.Inherited", "");
        try (var parser = JavaTestDeclarations.open()) {
            for (String name : names) {
                String qualified = unit.qualified(name, symbols);
                String source = known.get(qualified);
                if (source == null || source.isEmpty()) continue;
                var declaration = parser.parse(source);
                if (declaration.types().stream().flatMap(t -> t.annotations().stream())
                        .anyMatch(a -> declaration.qualified(a, symbols).equals("java.lang.annotation.Inherited")))
                    inherited.add(name);
            }
        }
        return resolve(inherited, unit, TestLayers.LAYERS);
    }
    private List<String> resolve(List<String> names, JavaTestDeclarations.Unit unit, Set<String> terminals,
                                 Set<String> visiting, JavaTestDeclarations.Parser parser) {
        var result = new ArrayList<String>();
        for (String name : names) {
            String qualified = unit.qualified(name, known);
            if (terminals.contains(qualified)) result.add(qualified);
            else if (known.containsKey(qualified) && !known.get(qualified).isEmpty()) {
                if (!visiting.add(qualified)) throw new IllegalArgumentException("GOV-ANNOTATION-CYCLE: " + qualified);
                try {
                    var meta = parser.parse(known.get(qualified));
                    for (var type : meta.types()) result.addAll(resolve(type.annotations(), meta, terminals, visiting, parser));
                } finally { visiting.remove(qualified); }
            }
        }
        return result;
    }
}

package atf.rentz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.IOException;
import java.nio.file.Path;

final class YamlDocuments {
    private YamlDocuments() { }
    static JsonNode read(Path file) throws IOException {
        var factory=new YAMLFactory().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
        var mapper=new ObjectMapper(factory);
        try(var parser=factory.createParser(file.toFile())) {
            JsonNode root=mapper.readTree(parser);
            if(parser.nextToken()!=null)throw new IllegalArgumentException("Exactly one YAML document required");
            return root;
        }
    }
}

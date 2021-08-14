package gsrs.module.substance.datasource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gsrs.module.substance.processors.CodeSystemUrlGenerator;

import ix.ginas.models.v1.Code;
import org.springframework.core.io.ClassPathResource;

public class DefaultCodeSystemUrlGenerator implements DataSet<CodeSystemMeta>, CodeSystemUrlGenerator {

    @JsonIgnore
    private final Map<String, CodeSystemMeta> map = new LinkedHashMap<>();

    public DefaultCodeSystemUrlGenerator(Map with) throws IOException {
        Map<String, String> codeMaps = (Map<String, String>) with.get("codeSystems");
        codeMaps.keySet().stream().map((codeSystem) -> {
            String url = codeMaps.get(codeSystem);
            CodeSystemMeta csmap = new CodeSystemMeta(codeSystem, url);
            return csmap;
        }).forEachOrdered((csmap) -> {
            map.put(csmap.codeSystem.toLowerCase(), csmap);
        });

//        try(InputStream is=new ClassPathResource(filename).getInputStream();) {
//            ObjectMapper mapper = new ObjectMapper();
//            JsonNode tree = mapper.readTree(is);
//
//            for (JsonNode jsn : tree) {
//                String cs = jsn.at("/codeSystem").asText();
//                String url = jsn.at("/url").asText();
//                CodeSystemMeta csmap = new CodeSystemMeta(cs, url);
//                map.put(csmap.codeSystem.toLowerCase(), csmap);
//            }
//          }
    }

    @JsonCreator
    public DefaultCodeSystemUrlGenerator(@JsonProperty("filename") String filename) throws IOException {
        try (InputStream is = new ClassPathResource(filename).getInputStream();) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode tree = mapper.readTree(is);

            for (JsonNode jsn : tree) {
                String cs = jsn.at("/codeSystem").asText();
                String url = jsn.at("/url").asText();
                CodeSystemMeta csmap = new CodeSystemMeta(cs, url);
                map.put(csmap.codeSystem.toLowerCase(), csmap);
            }
        }
    }

    @Override
    public Iterator<CodeSystemMeta> iterator() {
        return map.values().iterator();
    }

    @Override
    public boolean contains(CodeSystemMeta k) {
        return map.containsKey(k.codeSystem.toLowerCase());
    }

    public CodeSystemMeta fetch(String codesystem) {
        return this.map.get(codesystem.toLowerCase());
    }

    @Override
    public Optional<String> generateUrlFor(Code code) {
        if (code.code == null) {
            return Optional.empty();
        }
        CodeSystemMeta meta = fetch(code.codeSystem);
        if (meta == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(meta.generateUrlFor(code));
    }
}
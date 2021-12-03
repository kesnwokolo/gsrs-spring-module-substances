package ix.ginas.models.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import ix.core.controllers.EntityFactory.EntityMapper;
import ix.ginas.models.v1.Amount;
import ix.ginas.models.v1.GinasChemicalStructure;
import ix.ginas.models.v1.Moiety;

import java.io.IOException;

public class MoietyDeserializer extends JsonDeserializer<Moiety> {
    public MoietyDeserializer () {
    }

    public Moiety deserialize (JsonParser parser, DeserializationContext ctx)
        throws IOException, JsonProcessingException {
        JsonNode tree = parser.getCodec().readTree(parser);
        Moiety moiety = new Moiety();
        moiety.structure =
            parser.getCodec().treeToValue(tree, GinasChemicalStructure.class);
        JsonNode n = tree.get("count");
        if (n != null) {
        	try{
        		moiety.setCount(n.asInt());
        	}catch(Exception e){
        		Amount amnt= EntityMapper.FULL_ENTITY_MAPPER().treeToValue(n, Amount.class);
        		moiety.setCountAmount(amnt);
        	}
        }
        JsonNode namnt = tree.get("countAmount");
        if (namnt != null) {
        	try{
        		Amount amnt= EntityMapper.FULL_ENTITY_MAPPER().treeToValue(namnt, Amount.class);
        		moiety.setCountAmount(amnt);
        	}catch(Exception e){
        		System.err.println(e.getMessage());
        	}
        }
        moiety.enforce();
        return moiety;
    }
}

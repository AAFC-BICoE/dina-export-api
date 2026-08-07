package ca.gc.aafc.dina.export.api.generator.helper;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;

import static org.junit.jupiter.api.Assertions.assertNull;

public class DarwinCoreMapperTest {

  @Test
  public void extractValue() throws JsonProcessingException {

    DarwinCoreMapper mapper = new DarwinCoreMapper();

    DarwinCoreExportConfig.ColumnMapping mapping = new DarwinCoreExportConfig.ColumnMapping();
    mapping.setContext("collectingEvent");
    mapping.setDwcTerm("county");
    mapping.setFilter("@.placeType == 'county'");
    mapping.setSource("geographicPlaceNameSourceDetail.higherGeographicPlaces");
    mapping.setPath("name");

    String json = """
        {
          "geographicPlaceNameSourceDetail": {
            "higherGeographicPlaces": [
              {
                "id": "string",
                "element": "string",
                "placeType": "notcounty",
                "name": "string"
              }
            ]
          }
        }
    """;

    ObjectMapper objectMapper = new ObjectMapper();
    Map<String, JsonNode > entitiesContext = Map.of("collectingEvent", objectMapper.readTree(json));

    assertNull(mapper.extractValue(entitiesContext, mapping));
  }
}

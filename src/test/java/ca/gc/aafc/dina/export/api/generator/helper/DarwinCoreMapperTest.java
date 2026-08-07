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
                "sourceUrl": "https://nominatim.openstreetmap.org/ui/details.html?osmtype=R&osmid=62650",
                "customGeographicPlace": null,
                "selectedGeographicPlace": {
                    "id": "62650",
                    "element": "R",
                    "placeType": "ISO3166-2-lvl4",
                    "name": "DE-HE"
                },
                "higherGeographicPlaces": null,
                "stateProvince": {
                    "id": "62650",
                    "element": "relation",
                    "placeType": "state",
                    "name": "Hesse"
                },
                "country": {
                    "code": "de",
                    "name": "Germany"
                },
                "recordedOn": "2026-08-07T17:38:23.075989879Z"
            }
        }
    """;

    ObjectMapper objectMapper = new ObjectMapper();
    Map<String, JsonNode > entitiesContext = Map.of("collectingEvent", objectMapper.readTree(json));
    assertNull(mapper.extractValue(entitiesContext, mapping));
  }
}

package ca.gc.aafc.dina.export.api.generator.helper;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
import ca.gc.aafc.dina.export.api.service.DinaApiClient;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import okhttp3.HttpUrl;

public class DarwinCoreMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void extractValue() throws JsonProcessingException {

    DarwinCoreMapper mapper = new DarwinCoreMapper(mock(DinaApiClient.class));

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

  @Test
  public void extractValue_joinsToManyValues() throws JsonProcessingException {

    DarwinCoreMapper mapper = new DarwinCoreMapper(mock(DinaApiClient.class));

    DarwinCoreExportConfig.ColumnMapping mapping = new DarwinCoreExportConfig.ColumnMapping();
    mapping.setContext("attachment");
    mapping.setDwcTerm("associatedSequences");
    mapping.setSource("managedAttributes.ena_run_accession");

    String json = """
        [
          { "managedAttributes": { "ena_run_accession": "U3485313" } },
          { "managedAttributes": { "ena_run_accession": "GU328060" } }
        ]
        """;

    ObjectMapper objectMapper = new ObjectMapper();
    Map<String, JsonNode> entitiesContext = Map.of("attachment", objectMapper.readTree(json));
    assertEquals("U3485313 | GU328060", mapper.extractValue(entitiesContext, mapping));
  }

  @Test
  public void extractValue_resolvesVocabularyUriTemplate() throws JsonProcessingException {

    DinaApiClient client = mock(DinaApiClient.class);
    when(client.fetchDocument(any(HttpUrl.class))).thenReturn(vocabularyItemDocument());

    DarwinCoreMapper mapper = new DarwinCoreMapper(client);

    DarwinCoreExportConfig.ColumnMapping mapping = new DarwinCoreExportConfig.ColumnMapping();
    mapping.setContext("attachment");
    mapping.setDwcTerm("associatedSequences");
    mapping.setSource("managedAttributes");
    mapping.setVocabularyValue("uriTemplate");
    mapping.setVocabularyKey("ena_run_accession");
    mapping.setValuePlaceholder("$1");
    mapping.setVocabularyUrlTemplate("http://localhost:8081/api/v1/controlled-vocabulary-item");
    mapping.setDinaComponent("METADATA");

    String json = """
        [
          { "managedAttributes": { "ena_run_accession": "U3485313" } },
          { "managedAttributes": { "ena_run_accession": "GU328060" } }
        ]
        """;

    Map<String, JsonNode> entitiesContext = Map.of("attachment", objectMapper.readTree(json));
    String expected = "https://www.ebi.ac.uk/ena/browser/view/U3485313 | https://www.ebi.ac.uk/ena/browser/view/GU328060";
    assertEquals(expected, mapper.extractValue(entitiesContext, mapping));
  }

  @Test
  public void extractValue_resolvesVocabularyUriTemplate_onlyConfiguredKey() throws JsonProcessingException {

    DinaApiClient client = mock(DinaApiClient.class);
    when(client.fetchDocument(any(HttpUrl.class))).thenReturn(vocabularyItemDocument());

    DarwinCoreMapper mapper = new DarwinCoreMapper(client);

    DarwinCoreExportConfig.ColumnMapping mapping = new DarwinCoreExportConfig.ColumnMapping();
    mapping.setContext("attachment");
    mapping.setDwcTerm("associatedSequences");
    mapping.setSource("managedAttributes");
    mapping.setVocabularyValue("uriTemplate");
    mapping.setVocabularyKey("ena_run_accession");
    mapping.setValuePlaceholder("$1");
    mapping.setVocabularyUrlTemplate("http://localhost:8081/api/v1/controlled-vocabulary-item");
    mapping.setDinaComponent("METADATA");

    String json = """
        [
          { "managedAttributes": { "ena_run_accession": "U3485313", "other_key": "OTHER" } },
          { "managedAttributes": { "ena_run_accession": "GU328060" } }
        ]
        """;

    Map<String, JsonNode> entitiesContext = Map.of("attachment", objectMapper.readTree(json));
    String expected = "https://www.ebi.ac.uk/ena/browser/view/U3485313 | https://www.ebi.ac.uk/ena/browser/view/GU328060";
    assertEquals(expected, mapper.extractValue(entitiesContext, mapping));
    verify(client, never()).fetchDocument(argThat(url -> url.toString().contains("other_key")));
  }

  @Test
  public void extractValue_resolvesVocabulary_noUrlTemplate_skipsResolution() throws JsonProcessingException {
    DinaApiClient client = mock(DinaApiClient.class);

    DarwinCoreMapper mapper = new DarwinCoreMapper(client);

    DarwinCoreExportConfig.ColumnMapping mapping = new DarwinCoreExportConfig.ColumnMapping();
    mapping.setContext("attachment");
    mapping.setDwcTerm("associatedSequences");
    mapping.setSource("managedAttributes");
    mapping.setVocabularyValue("uriTemplate");
    mapping.setVocabularyKey("ena_run_accession");
    // vocabularyUrlTemplate intentionally not set: resolution should be skipped

    String json = """
        [
          { "managedAttributes": { "ena_run_accession": "U3485313" } }
        ]
        """;

    Map<String, JsonNode> entitiesContext = Map.of("attachment", objectMapper.readTree(json));
    assertNull(mapper.extractValue(entitiesContext, mapping));
    verify(client, never()).fetchDocument(any(HttpUrl.class));
  }

  private JsonApiDocument vocabularyItemDocument() {
    return JsonApiDocument.builder()
      .data(JsonApiDocument.ResourceObject.builder()
        .id(UUID.fromString("01a03daf-fe1d-7731-a3b9-6e488ea67d4e"))
        .type("controlled-vocabulary-item")
        .attributes(Map.of("uriTemplate", "https://www.ebi.ac.uk/ena/browser/view/$1"))
        .build())
      .build();
  }
}

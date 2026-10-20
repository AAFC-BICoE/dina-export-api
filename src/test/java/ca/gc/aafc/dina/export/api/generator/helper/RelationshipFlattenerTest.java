package ca.gc.aafc.dina.export.api.generator.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.gc.aafc.dina.export.api.config.JsonPathConfiguration;
import ca.gc.aafc.dina.export.api.service.DinaApiClient;
import ca.gc.aafc.dina.export.api.service.ExternalRelationshipResolver;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import okhttp3.HttpUrl;

import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.MAP_TYPEREF;

public class RelationshipFlattenerTest {

  private static final String PERSON_1 = "bdd8f5b4-8f22-42f3-bd62-c1ddbf6d3576";
  private static final String PERSON_2 = "0a6b8c1e-7a0f-4d1c-9b3a-2f0c5e1d4a77";

  // Material sample with a collecting event that only references its collectors (not in the document)
  private static final String RECORD = """
    {
      "data": {
        "id": "01a111bc-47c2-743b-9546-873d1bf62f1a",
        "type": "material-sample",
        "attributes": { "materialSampleName": "sample 1" },
        "relationships": {
          "collectingEvent": { "data": { "id": "01a11174-2e31-77ee-a509-cf1c73db68a4", "type": "collecting-event" } }
        }
      },
      "included": [
        {
          "id": "01a11174-2e31-77ee-a509-cf1c73db68a4",
          "type": "collecting-event",
          "attributes": { "createdBy": "dina-su" },
          "relationships": {
            "collectors": { "data": [
              { "id": "%s", "type": "person" },
              { "id": "%s", "type": "person" }
            ] },
            "expedition": { "data": { "id": "01a11174-2e31-77ee-a509-cf1c73db6000", "type": "expedition" } }
          }
        }
      ]
    }
    """.formatted(PERSON_1, PERSON_2);

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void mergeRelationshipsIntoAttributes_onExternalRelationship_collectorsResolved()
      throws JsonProcessingException {
    DinaApiClient client = mockAgentApi();
    ObjectNode attributes = objectMapper.createObjectNode();

    newFlattener(client, Set.of("collectingEvent.collectors", "collectingEvent.expedition"))
      .mergeRelationshipsIntoAttributes(objectMapper.readTree(RECORD), attributes);

    assertEquals("dina-su", attributes.get("collectingEvent.createdBy").asText());

    // the export flattens nested maps to dot notation, giving the collectingEvent.collectors.displayName column
    Map<String, Object> columns = JSONApiDocumentStructure.extractNestedMapUsingDotNotation(
      objectMapper.convertValue(attributes, MAP_TYPEREF)).nestedMapsMap();
    assertEquals("Bob Tester;Jane Doe", columns.get("collectingEvent.collectors.displayName"));

    // expedition is not an externally resolvable type
    assertFalse(attributes.has("collectingEvent.expedition"));
  }

  @Test
  public void mergeRelationshipsIntoAttributes_onNotRequested_notResolved() throws JsonProcessingException {
    DinaApiClient client = mockAgentApi();
    ObjectNode attributes = objectMapper.createObjectNode();

    newFlattener(client, Set.of()).mergeRelationshipsIntoAttributes(objectMapper.readTree(RECORD), attributes);

    assertFalse(attributes.has("collectingEvent.collectors"));
    verify(client, never()).fetchDocument(any(HttpUrl.class));
  }

  @Test
  public void toFlatEntity_onExternalRelationship_collectorsEmbedded() throws JsonProcessingException {
    JsonNode record = objectMapper.readTree(RECORD);

    ObjectNode flat = RelationshipFlattener.toFlatEntity(objectMapper, record.get("data"), record,
      new ExternalRelationshipResolver(mockAgentApi(), "http://localhost:8082/api/v1", "person,organization",
        "http://localhost:8081/api/v1", "metadata"),
      Set.of("collectingEvent.collectors"));

    JsonNode collectors = flat.at("/collectingEvent/collectors");
    assertEquals(2, collectors.size());
    assertEquals("Bob Tester", collectors.get(0).get("displayName").asText());
    assertEquals(PERSON_2, collectors.get(1).get("id").asText());
  }

  private RelationshipFlattener newFlattener(DinaApiClient client, Set<String> externalRelationshipPaths) {
    return new RelationshipFlattener(objectMapper, new JsonPathConfiguration().provideJsonPathConfiguration(),
      new ExternalRelationshipResolver(client, "http://localhost:8082/api/v1", "person,organization",
        "http://localhost:8081/api/v1", "metadata"), externalRelationshipPaths);
  }

  private static DinaApiClient mockAgentApi() {
    Map<String, String> displayNames = Map.of(PERSON_1, "Bob Tester", PERSON_2, "Jane Doe");
    DinaApiClient client = mock(DinaApiClient.class);
    when(client.fetchDocument(any(HttpUrl.class))).thenAnswer(invocation -> {
      HttpUrl url = invocation.getArgument(0);
      String id = url.pathSegments().getLast();
      if (!url.encodedPath().contains("/person/") || !displayNames.containsKey(id)) {
        return null;
      }
      return JsonApiDocument.builder()
        .data(JsonApiDocument.ResourceObject.builder()
          .id(UUID.fromString(id))
          .type("person")
          .attributes(Map.of("displayName", displayNames.get(id)))
          .build())
        .build();
    });
    return client;
  }
}

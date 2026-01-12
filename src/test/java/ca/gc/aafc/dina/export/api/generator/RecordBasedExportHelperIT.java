package ca.gc.aafc.dina.export.api.generator;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.ElasticSearchTestContainerInitializer;
import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RecordBasedExportHelper which contains shared functionality
 * used by both TabularDataExportGenerator and NormalizedExportGenerator.
 */
@ContextConfiguration(initializers = {ElasticSearchTestContainerInitializer.class})
public class RecordBasedExportHelperIT extends BaseIntegrationTest {

  private static final String TEST_INDEX = "dina_test_helper_index";

  @Inject
  private RecordBasedExportHelper recordHelper;

  @Inject
  private ElasticsearchClient esClient;

  @Inject
  private ObjectMapper objectMapper;

  @BeforeEach
  public void setupElasticSearch() throws IOException {
    try {
      esClient.indices().delete(d -> d.index(TEST_INDEX));
    } catch (Exception e) {
      // Ignore if index doesn't exist
    }
  }

  @AfterEach
  public void cleanupElasticSearch() throws IOException {
    try {
      esClient.indices().delete(d -> d.index(TEST_INDEX));
    } catch (Exception e) {
      // Ignore cleanup errors
    }
  }

  // Note: prepareAttributeNode is tested through integration tests like NormalizedExportIT 
  // and TabularDataExportWithAliasesIT since it requires ElasticSearch data structure

  @Test
  public void testApplyFunctions_concatFunction() throws IOException {
    // Given: an attribute node and a CONCAT function
    ObjectNode attributeNode = objectMapper.createObjectNode();
    attributeNode.put("firstName", "John");
    attributeNode.put("lastName", "Doe");

    DataExportFunction concatFunction = new DataExportFunction(
        DataExportFunction.FunctionDef.CONCAT,
        Map.of(
            "items", List.of("firstName", "lastName", "constant1"),
            "constants", Map.of("constant1", "(Test)"),
            "separator", " "
        )
    );

    Map<String, DataExportFunction> functions = Map.of("fullName", concatFunction);

    // When: applying functions
    recordHelper.applyFunctions(attributeNode, functions);

    // Then: should have concatenated result
    assertTrue(attributeNode.has("fullName"));
    assertEquals("John Doe (Test)", attributeNode.get("fullName").asText());
  }

  @Test
  public void testApplyFunctions_coordinatesConversion() throws IOException {
    // Given: an attribute node with geo_point coordinates [longitude, latitude]
    ObjectNode attributeNode = objectMapper.createObjectNode();
    attributeNode.putArray("coordinates").add(-62.37839).add(46.45451);

    DataExportFunction coordsFunction = new DataExportFunction(
        DataExportFunction.FunctionDef.CONVERT_COORDINATES_DD,
        Map.of("column", "coordinates")
    );

    Map<String, DataExportFunction> functions = Map.of("decimalCoords", coordsFunction);

    // When: applying functions
    recordHelper.applyFunctions(attributeNode, functions);

    // Then: should have converted coordinates as "lat,lon"
    assertTrue(attributeNode.has("decimalCoords"));
    String result = attributeNode.get("decimalCoords").asText();
    assertTrue(result.matches("46\\.\\d+,-62\\.\\d+"), "Should be in format 'lat,lon'");
  }

  @Test
  public void testFlatRelationships_withSimpleRelationship() throws IOException {
    // Given: a document with a to-one relationship
    UUID collectionId = UUID.randomUUID();
    String jsonDoc = """
    {
      "data": {
        "id": "test-id",
        "type": "material-sample",
        "attributes": {
          "materialSampleName": "TEST-004"
        },
        "relationships": {
          "collection": {
            "data": {
              "id": "%s",
              "type": "collection"
            }
          }
        }
      },
      "included": [
        {
          "id": "%s",
          "type": "collection",
          "attributes": {
            "name": "Test Collection",
            "code": "TC-001"
          }
        }
      ]
    }
    """.formatted(collectionId, collectionId);

    JsonNode record = objectMapper.readTree(jsonDoc);

    // When: flattening relationships
    Map<String, Object> result = recordHelper.flatRelationships(record);

    // Then: should have flattened relationship
    assertNotNull(result);
    assertTrue(result.containsKey("collection"));
    Map<?, ?> collectionData = (Map<?, ?>) result.get("collection");
    assertEquals("Test Collection", collectionData.get("name"));
    assertEquals("TC-001", collectionData.get("code"));
  }

  @Test
  public void testFlatToMany_concatenatesValues() {
    // Given: multiple maps to merge
    List<Map<String, Object>> toMerge = List.of(
        Map.of("id", "id1", "name", "Name1", "status", "Active"),
        Map.of("id", "id2", "name", "Name2", "status", "Inactive")
    );

    // When: flattening to-many relationship
    Map<String, Object> result = RecordBasedExportHelper.flatToMany(toMerge);

    // Then: should concatenate values with semicolon
    assertEquals("id1;id2", result.get("id"));
    assertEquals("Name1;Name2", result.get("name"));
    assertEquals("Active;Inactive", result.get("status"));
  }

  @Test
  public void testFlatToMany_handlesNullValues() {
    // Given: maps with null values
    List<Map<String, Object>> toMerge = List.of(
        Map.of("id", "id1", "name", "Name1"),
        Map.of("id", "id2", "name", "Name2")
    );

    // When: flattening (some maps don't have all keys)
    Map<String, Object> result = RecordBasedExportHelper.flatToMany(toMerge);

    // Then: should handle missing keys gracefully
    assertEquals("id1;id2", result.get("id"));
    assertEquals("Name1;Name2", result.get("name"));
    assertFalse(result.containsKey("status"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExtractById_findsDocument() throws IOException {
    // Given: a list of documents with an included section
    String targetId = UUID.randomUUID().toString();
    String jsonDoc = """
    [
      {
        "id": "other-id",
        "type": "project",
        "attributes": {
          "name": "Other Project"
        }
      },
      {
        "id": "%s",
        "type": "project",
        "attributes": {
          "name": "Target Project"
        }
      }
    ]
    """.formatted(targetId);

    List<Map<String, Object>> document = objectMapper.readValue(jsonDoc, List.class);

    // When: extracting by ID
    Map<String, Object> result = recordHelper.extractById(targetId, document);

    // Then: should find the target document
    assertNotNull(result);
    assertEquals(targetId, result.get("id"));
    Map<?, ?> attributes = (Map<?, ?>) result.get("attributes");
    assertEquals("Target Project", attributes.get("name"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExtractById_returnsEmptyWhenNotFound() throws IOException {
    // Given: a list of documents without the target ID
    String jsonDoc = """
    [
      {
        "id": "other-id",
        "type": "project",
        "attributes": {
          "name": "Other Project"
        }
      }
    ]
    """;

    List<Map<String, Object>> document = objectMapper.readValue(jsonDoc, List.class);

    // When: extracting non-existent ID
    Map<String, Object> result = recordHelper.extractById("non-existent-id", document);

    // Then: should return empty map
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }
}

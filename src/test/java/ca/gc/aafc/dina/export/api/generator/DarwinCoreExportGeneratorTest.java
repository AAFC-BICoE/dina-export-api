package ca.gc.aafc.dina.export.api.generator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreContextBuilder;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreMapper;
import ca.gc.aafc.dina.export.api.output.TabularOutput;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for DarwinCore mapping logic.
 *
 * Verifies that given an Elasticsearch JSON:API source document,
 * the context builder and mapper produce the expected DwC record.
 *
 * Input: the full JSON:API document (data + included), denormalized so that
 * organism, collectingEvent and collection are embedded as direct fields
 * in the materialSample entity node — matching what buildContextMap expects.
 */
public class DarwinCoreExportGeneratorTest {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private static final String EXPECTED_OCCURRENCE_ID  = "019e6085-f541-71ae-a15a-f7e971049c6b";
  private static final String EXPECTED_CATALOG_NUMBER = "TEST_COL";
  private static final String EXPECTED_COLLECTION_CODE = "TEST_COL";
  private static final String EXPECTED_EVENT_DATE      = "2025-05-10T12:30:11";
  private static final String EXPECTED_BASIS_OF_RECORD = "PreservedSpecimen";
  private static final String EXPECTED_SCIENTIFIC_NAME_FROM_DET = "Procavia capensis (Pallas, 1766)";
  private static final String EXPECTED_KINGDOM = "Animalia";

  private static DarwinCoreExportConfig config;
  private static DarwinCoreContextBuilder contextBuilder;
  private static DarwinCoreMapper mapper;
  private static JsonNode esSource;  // full JSON:API document as stored in Elasticsearch

  // -------------------------------------------------------------------------
  // Setup
  // -------------------------------------------------------------------------

  @BeforeAll
  static void setUp() throws IOException {
    config = loadConfigFromYaml();
    contextBuilder = new DarwinCoreContextBuilder(config);
    mapper = new DarwinCoreMapper();

    try (InputStream is = DarwinCoreExportGeneratorTest.class
        .getResourceAsStream("/elasticsearch/material_sample_response.json")) {
      esSource = JSON_MAPPER.readTree(is);
    }
  }

  // -------------------------------------------------------------------------
  // Context-map tests
  // -------------------------------------------------------------------------

  @Test
  void contextMap_allExpectedContextsArePresent() {
    ObjectNode entity = buildDenormalizedEntity(esSource);
    Map<String, JsonNode> ctx = contextBuilder.buildContextMap(entity);

    assertNotNull(ctx.get("materialSample"),        "materialSample context missing");
    assertNotNull(ctx.get("organism"),              "organism context missing");
    assertNotNull(ctx.get("collectingEvent"),        "collectingEvent context missing");
    assertNotNull(ctx.get("geoReferenceAssertions"), "geoReferenceAssertions context missing");
    assertNotNull(ctx.get("determination"),          "determination context missing");
  }

  @Test
  void contextMap_organism_isPrimaryOrganismSelected() {
    ObjectNode entity = buildDenormalizedEntity(esSource);
    Map<String, JsonNode> ctx = contextBuilder.buildContextMap(entity);

    JsonNode organism = ctx.get("organism");
    assertNotNull(organism);
    assertEquals(EXPECTED_OCCURRENCE_ID, organism.get("id").asText());
    assertEquals("true", organism.get("isPrimary").asText());
  }

  @Test
  void contextMap_determination_isPrimaryDeterminationSelected() {
    ObjectNode entity = buildDenormalizedEntity(esSource);
    Map<String, JsonNode> ctx = contextBuilder.buildContextMap(entity);

    JsonNode det = ctx.get("determination");
    assertNotNull(det);
    assertEquals("true", det.get("isPrimary").asText());
    assertEquals(EXPECTED_SCIENTIFIC_NAME_FROM_DET, det.get("scientificName").asText());
  }

  @Test
  void contextMap_geoReferenceAssertions_isPrimarySelected() {
    ObjectNode entity = buildDenormalizedEntity(esSource);
    Map<String, JsonNode> ctx = contextBuilder.buildContextMap(entity);

    JsonNode geoRef = ctx.get("geoReferenceAssertions");
    assertNotNull(geoRef);
    assertEquals("true", geoRef.get("isPrimary").asText());
    assertEquals("52.0", geoRef.get("dwcDecimalLatitude").asText());
  }

  // -------------------------------------------------------------------------
  // DwC record mapping tests
  // -------------------------------------------------------------------------

  /**
   * Verifies all DwC field mappings in a single pass.
   */
  @Test
  void dwcRecord_allExpectedFieldsMapped() {
    Map<String, String> record = buildDwcRecord(esSource);

    assertEquals(EXPECTED_OCCURRENCE_ID,            record.get("occurrenceID"));
    assertEquals(EXPECTED_SCIENTIFIC_NAME_FROM_DET, record.get("scientificName"));
    assertEquals(EXPECTED_KINGDOM,                  record.get("kingdom"));
    assertEquals(EXPECTED_CATALOG_NUMBER,           record.get("catalogNumber"));
    assertEquals(EXPECTED_COLLECTION_CODE,          record.get("collectionCode"));
    assertEquals(EXPECTED_EVENT_DATE,               record.get("eventDate"));
    assertEquals("Test locality",                   record.get("locality"));
    assertEquals("52.0",                            record.get("decimalLatitude"));
    assertEquals("Ontario",                         record.get("stateProvince"));
    assertEquals("Stormont, Dundas and Glengarry Counties", record.get("county"));
    assertEquals(EXPECTED_BASIS_OF_RECORD,          record.get("basisOfRecord"));
  }


  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Builds the DwC record from the full Elasticsearch source document by:
   * 1. Denormalizing the entity (embed organism/collectingEvent/collection)
   * 2. Building the context map
   * 3. Applying every column mapping
   */
  private static Map<String, String> buildDwcRecord(JsonNode source) {
    ObjectNode entity = buildDenormalizedEntity(source);
    Map<String, JsonNode> contextMap = contextBuilder.buildContextMap(entity);

    Map<String, String> record = new LinkedHashMap<>();
    for (DarwinCoreExportConfig.ColumnMapping col : config.getOccurrence().getColumns()) {
      String value = mapper.extractValue(contextMap, col);
      record.put(col.getDwcTerm(), value != null ? value : "");
    }
    return record;
  }

  /**
   * Converts the full JSON:API Elasticsearch source into a flat entity node
   * that DarwinCoreContextBuilder can navigate.
   *
   * The contextBuilder expects:
   * - attributes accessible at the root level
   * - organism      → array with isPrimary flag on each element
   * - collectingEvent → object with startEventDateTime, geoReferenceAssertions, etc.
   * - collection      → object with code, name, etc.
   */
  private static ObjectNode buildDenormalizedEntity(JsonNode source) {
    JsonNode included = source.get("included");

    // Start from data.attributes so all materialSample fields are at the root level
    ObjectNode entity = (ObjectNode) source.at("/data/attributes").deepCopy();
    entity.put("id", source.at("/data/id").asText());

    // Embed organisms (from included), marking the first one as isPrimary
    ArrayNode organisms = JSON_MAPPER.createArrayNode();
    boolean firstOrganism = true;
    for (JsonNode inc : included) {
      if ("organism".equals(inc.at("/type").asText())) {
        ObjectNode org = (ObjectNode) inc.get("attributes").deepCopy();
        org.put("id", inc.at("/id").asText());
        org.put("isPrimary", firstOrganism);
        organisms.add(org);
        firstOrganism = false;
      }
    }
    entity.set("organism", organisms);

    // Embed collectingEvent (first match in included)
    for (JsonNode inc : included) {
      if ("collecting-event".equals(inc.at("/type").asText())) {
        ObjectNode ce = (ObjectNode) inc.get("attributes").deepCopy();
        ce.put("id", inc.at("/id").asText());
        entity.set("collectingEvent", ce);
        break;
      }
    }

    // Embed collection (first match in included)
    for (JsonNode inc : included) {
      if ("collection".equals(inc.at("/type").asText())) {
        entity.set("collection", inc.get("attributes").deepCopy());
        break;
      }
    }

    return entity;
  }

  /**
   * Loads DarwinCoreExportConfig from the classpath YAML using Jackson YAML support.
   */
  private static DarwinCoreExportConfig loadConfigFromYaml() throws IOException {
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    try (InputStream is = DarwinCoreExportGeneratorTest.class
        .getResourceAsStream("/dwc/darwincore-mapping.yaml")) {
      JsonNode root = yamlMapper.readTree(is);
      DarwinCoreExportConfig cfg = new DarwinCoreExportConfig();
      cfg.setOccurrence(
          yamlMapper.treeToValue(root.at("/dwc/occurrence"),
              DarwinCoreExportConfig.Occurrence.class));
      return cfg;
    }
  }
}

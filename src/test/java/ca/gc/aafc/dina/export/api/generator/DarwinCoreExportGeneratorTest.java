package ca.gc.aafc.dina.export.api.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreContextBuilder;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for DarwinCore context building, field mapping, and CSV generation.
 *
 * Generated files are written to target/test-output/dwc/ for local inspection after the run.
 */
public class DarwinCoreExportGeneratorTest {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private static DarwinCoreExportConfig config;
  private static DarwinCoreExportGenerator generator;
  private static JsonNode esSource;

  // -------------------------------------------------------------------------
  // Setup
  // -------------------------------------------------------------------------

  @BeforeAll
  static void setUp() throws IOException {
    config = loadConfigFromYaml();
    generator = new DarwinCoreExportGenerator(
        null, null, null, null, null, null,
        config, new DarwinCoreContextBuilder(config), new DarwinCoreMapper());

    try (InputStream is = DarwinCoreExportGeneratorTest.class
        .getResourceAsStream("/elasticsearch/material_sample_response.json")) {
      esSource = JSON_MAPPER.readTree(is);
    }
  }

  // -------------------------------------------------------------------------
  // CSV generation test
  // -------------------------------------------------------------------------

  @Test
  void generateOccurrenceCsv_writesValidCsv(@TempDir Path tempDir) throws Exception {
    ObjectNode entity = buildDenormalizedEntity(esSource);
    Path csvPath = tempDir.resolve("occurrence.csv");

    generator.generateOccurrenceCsv(List.of(entity), csvPath);

    CsvMapper csvMapper = new CsvMapper();
    CsvSchema schema = CsvSchema.emptySchema().withHeader();
    com.fasterxml.jackson.databind.MappingIterator<Map<String, String>> it = csvMapper
        .<Map<String, String>>readerForMapOf(String.class)
        .with(schema)
        .readValues(csvPath.toFile());
    List<Map<String, String>> records = it.readAll();

    assertEquals(1, records.size(), "Expected exactly one data row");
    Map<String, String> row = records.get(0);

    // All configured DwC terms appear as headers
    for (DarwinCoreExportConfig.ColumnMapping col : config.getOccurrence().getColumns()) {
      assertTrue(row.containsKey(col.getDwcTerm()), "Missing column: " + col.getDwcTerm());
    }

    // Verify every mapped column has a non-blank value
    for (DarwinCoreExportConfig.ColumnMapping col : config.getOccurrence().getColumns()) {
      String value = row.get(col.getDwcTerm());
      assertFalse(value == null || value.isBlank(),
          "Column " + col.getDwcTerm() + " has blank value in CSV");
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

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

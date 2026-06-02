package ca.gc.aafc.dina.export.api.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import jakarta.inject.Inject;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.DinaExportModuleApiLauncher;
import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreContextBuilder;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for DarwinCore context building, field mapping, and CSV generation.
 *
 * Generated files are written to target/test-output/dwc/ for local inspection after the run.
 */
@SpringBootTest(classes = {BaseIntegrationTest.TestConfig.class, DinaExportModuleApiLauncher.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DarwinCoreExportGeneratorTest extends BaseIntegrationTest {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  @Inject
  private DarwinCoreExportConfig config;

  @Inject
  private freemarker.template.Configuration freemarkerConfig;

  private JsonNode esSource;

  // -------------------------------------------------------------------------
  // Setup
  // -------------------------------------------------------------------------

  @BeforeAll
  void setUp() throws IOException {
    try (InputStream is = DarwinCoreExportGeneratorTest.class
        .getResourceAsStream("/elasticsearch/material_sample_response.json")) {
      esSource = JSON_MAPPER.readTree(is);
    }
  }

  // -------------------------------------------------------------------------
  // CSV generation test
  // -------------------------------------------------------------------------

  @Test
  void generateArchive_csvContainsAllMappedColumns(@TempDir Path tempDir) throws Exception {
    DarwinCoreMetaXmlGenerator metaXmlGenerator = new DarwinCoreMetaXmlGenerator(config, freemarkerConfig);
    DarwinCoreExportGenerator gen = new DarwinCoreExportGenerator(
        null, null, null, null, JSON_MAPPER, null,
        config, new DarwinCoreContextBuilder(config), new DarwinCoreMapper(), metaXmlGenerator);

    Path zipPath = tempDir.resolve("occurrence.zip");

    DarwinCoreExportTestSupport.generateArchive(gen, List.of(esSource), zipPath);

    List<Map<String, String>> records;
    try (ZipFile zip = new ZipFile(zipPath.toFile())) {
      ZipEntry csvEntry = zip.getEntry("occurrence.csv");
      assertNotNull(csvEntry, "occurrence.csv must be present in archive");
      try (InputStream is = zip.getInputStream(csvEntry)) {
        CsvMapper csvMapper = new CsvMapper();
        MappingIterator<Map<String, String>> it = csvMapper
            .readerForMapOf(String.class)
            .with(CsvSchema.emptySchema().withHeader())
            .readValues(is);
        records = it.readAll();
      }
    }

    assertEquals(1, records.size(), "Expected exactly one data row");
    Map<String, String> row = records.get(0);

    for (DarwinCoreExportConfig.ColumnMapping col : config.getCore().getColumns()) {
      assertTrue(row.containsKey(col.getDwcTerm()), "Missing column: " + col.getDwcTerm());
      String value = row.get(col.getDwcTerm());
      assertFalse(value == null || value.isBlank(),
          "Column " + col.getDwcTerm() + " has blank value in CSV");
    }
  }

}


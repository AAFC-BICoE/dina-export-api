package ca.gc.aafc.dina.export.api.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import freemarker.template.Configuration;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for DarwinCoreMetaXmlGenerator.
 *
 * The meta.xml is generated once in setUp() and written to target/test-output/dwc/meta.xml
 * for local inspection after the run.
 */
public class DarwinCoreMetaXmlGeneratorTest {

  private static final String DWC_NS     = "http://rs.tdwg.org/dwc/terms/";

  private static DarwinCoreExportConfig config;
  private static Document doc;

  @BeforeAll
  static void setUp() throws Exception {
    config = loadConfigFromYaml();

    Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
    freemarkerConfig.setClassLoaderForTemplateLoading(
        DarwinCoreMetaXmlGeneratorTest.class.getClassLoader(), "/");

    DarwinCoreMetaXmlGenerator generator = new DarwinCoreMetaXmlGenerator(config, freemarkerConfig);

    Path tempFile = Files.createTempFile("meta", ".xml");
    try {
      generator.generateMetaXml(tempFile);
      try (InputStream is = Files.newInputStream(tempFile)) {
        doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
      }
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void generateMetaXml_fieldCountMatchesColumns() {
    assertEquals(config.getOccurrence().getColumns().size(),
        doc.getElementsByTagName("field").getLength());
  }

  @Test
  void generateMetaXml_namespacesAreCorrect() {
    NodeList fields = doc.getElementsByTagName("field");

    // occurrenceID is the first column — must use DWC namespace
    assertEquals(DWC_NS + "occurrenceID",
        ((Element) fields.item(0)).getAttribute("term"));

    // scientificName (index 1) — must use DWC namespace
    assertEquals(DWC_NS + "scientificName",
        ((Element) fields.item(1)).getAttribute("term"));
  }

  @Test
  void generateMetaXml_fieldIndicesAreSequential() {
    NodeList fields = doc.getElementsByTagName("field");
    for (int i = 0; i < fields.getLength(); i++) {
      assertEquals(String.valueOf(i),
          ((Element) fields.item(i)).getAttribute("index"),
          "Field at position " + i + " should have index=" + i);
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static DarwinCoreExportConfig loadConfigFromYaml() throws IOException {
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    try (InputStream is = DarwinCoreMetaXmlGeneratorTest.class
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

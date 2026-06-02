package ca.gc.aafc.dina.export.api.generator;

import jakarta.inject.Inject;

import freemarker.template.Configuration;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.DinaExportModuleApiLauncher;
import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/**
 * Unit tests for DarwinCoreMetaXmlGenerator.
 *
 * The meta.xml is generated once in setUp() and written to target/test-output/dwc/meta.xml
 * for local inspection after the run.
 */
@SpringBootTest(classes = {BaseIntegrationTest.TestConfig.class, DinaExportModuleApiLauncher.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DarwinCoreMetaXmlGeneratorTest extends BaseIntegrationTest {

  @Inject
  private DarwinCoreExportConfig config;

  @Inject
  private Configuration freemarkerConfig;

  private Document doc;

  @BeforeAll
  void setUp() throws Exception {
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
  void generateMetaXml_containsAllConfiguredTermsInOrder() {
    NodeList fields = doc.getElementsByTagName("field");

    List<String> expectedTerms = config.getCore().getColumns().stream()
        .map(DarwinCoreExportConfig.ColumnMapping::getTermUri)
        .toList();

    List<String> actualTerms = new ArrayList<>();
    for (int i = 0; i < fields.getLength(); i++) {
      actualTerms.add(((Element) fields.item(i)).getAttribute("term"));
    }

    assertIterableEquals(expectedTerms, actualTerms,
        "meta.xml field terms should exactly match darwincore-mapping terms in order");
  }

}

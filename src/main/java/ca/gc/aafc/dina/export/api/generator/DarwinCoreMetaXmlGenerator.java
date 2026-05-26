package ca.gc.aafc.dina.export.api.generator;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.extern.log4j.Log4j2;

import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;

/**
 * Generates meta.xml for DarwinCore Archive using Freemarker templates
 *
 * The template is at: src/main/resources/dwc/meta.xml.ftl
 */
@Component
@Log4j2
public class DarwinCoreMetaXmlGenerator {

  private final DarwinCoreExportConfig config;
  private final Configuration freemarkerConfig;

  private static final String DWC_NS = "http://rs.tdwg.org/dwc/terms/";
  private static final String DCTERMS_NS = "http://purl.org/dc/terms/";

  public DarwinCoreMetaXmlGenerator(DarwinCoreExportConfig config, Configuration freemarkerConfig) {
    this.config = config;
    this.freemarkerConfig = freemarkerConfig;
  }

  /**
   * Generate meta.xml file from Freemarker template
   *
   * @param metaXmlPath Path where to write meta.xml
   * @throws IOException if write fails
   */
  public void generateMetaXml(Path metaXmlPath) throws IOException {
    try {
      List<DarwinCoreExportConfig.ColumnMapping> columns = config.getOccurrence().getColumns();
      List<ColumnData> columnData = buildColumnData(columns);

      // Prepare template data
      Map<String, Object> data = new HashMap<>();
      data.put("columns", columnData);

      // Load and process template
      Template template = freemarkerConfig.getTemplate("dwc/meta.xml.ftl");

      try (FileWriter writer = new FileWriter(metaXmlPath.toFile(), StandardCharsets.UTF_8)) {
        template.process(data, writer);
      }

      log.info("Generated meta.xml at: {}", metaXmlPath);
    } catch (TemplateException e) {
      log.error("Error processing template: {}", e.getMessage(), e);
      throw new IOException("Failed to generate meta.xml: " + e.getMessage(), e);
    }
  }

  /**
   * Build column data for template
   */
  private List<ColumnData> buildColumnData(List<DarwinCoreExportConfig.ColumnMapping> columns) {
    return IntStream.range(0, columns.size())
      .mapToObj(i -> {
        DarwinCoreExportConfig.ColumnMapping mapping = columns.get(i);
        return new ColumnData(
          i,
          mapping.getDwcTerm(),
          getDwCUri(mapping.getDwcTerm()),
          getXsdDataType(mapping.getDataType())
        );
      })
      .toList();
  }

  /**
   * Get DwC URI for a term
   */
  private String getDwCUri(String dwcTerm) {
    return switch (dwcTerm) {
      case "occurrenceID", "informationWithheld", "dataGeneralizations" -> DCTERMS_NS + dwcTerm;
      default -> DWC_NS + dwcTerm;
    };
  }

  /**
   * Convert config dataType to XSD dataType
   */
  private String getXsdDataType(String configDataType) {
    if (configDataType == null) {
      return null;
    }

    return switch (configDataType.toLowerCase()) {
      case "string" -> "xsd:string";
      case "integer" -> "xsd:integer";
      case "decimal" -> "xsd:decimal";
      case "date" -> "xsd:date";
      case "datetime" -> "xsd:dateTime";
      default -> null;
    };
  }

  /**
   * Column data for meta.xml template
   *
   * @param index Column index (0-based)
   * @param dwcTerm DarwinCore term name
   * @param uri Full URI for the term
   * @param dataType XSD data type (nullable)
   */
  private record ColumnData(
    int index,
    String dwcTerm,
    String uri,
    String dataType
  ) {}
}

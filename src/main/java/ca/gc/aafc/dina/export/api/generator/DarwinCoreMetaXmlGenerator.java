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
 * The template is at: src/main/resources/templates/dwc/meta.xml.ftl
 */
@Component
@Log4j2
public class DarwinCoreMetaXmlGenerator {

  public static final String DEFAULT_META_FILENAME = "meta.xml";

  private static final String TEMPLATE_NAME = "dwc/meta.xml.ftl";

  private final DarwinCoreExportConfig config;
  private final Configuration freemarkerConfig;

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
      DarwinCoreExportConfig.Core core = config.getCore();
      List<DarwinCoreExportConfig.ColumnMapping> columns = core.getColumns();
      List<Map<String, Object>> columnData = buildColumnData(columns);

      // Prepare template data
      Map<String, Object> data = new HashMap<>();
      data.put("columns", columnData);
      data.put("rowType", core.getRowType());
      data.put("fileLocations", core.getFileLocations());

      // Load and process template
      Template template = freemarkerConfig.getTemplate(TEMPLATE_NAME);

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
   * Build column data maps for template
   */
  private List<Map<String, Object>> buildColumnData(List<DarwinCoreExportConfig.ColumnMapping> columns) {
    return IntStream.range(0, columns.size())
      .mapToObj(i -> {
        DarwinCoreExportConfig.ColumnMapping mapping = columns.get(i);
        Map<String, Object> col = new HashMap<>();
        col.put("dwcTerm",  mapping.getDwcTerm());
        col.put("uri",      mapping.getTermUri());
        col.put("dataType", getXsdDataType(mapping.getDataType()));
        return col;
      })
      .toList();
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

}

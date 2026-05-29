package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreContextBuilder;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreMapper;
import ca.gc.aafc.dina.export.api.output.DataOutput;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;
import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.messaging.producer.DinaMessageProducer;

import com.fasterxml.jackson.core.type.TypeReference;

import ca.gc.aafc.dina.export.api.output.TabularOutput;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DarwinCoreExportGenerator extends RecordBasedExportGenerator {

  private static final TypeReference<Map<String, String>> MAP_STRING_TYPEREF = new TypeReference<>() {};

  private final DarwinCoreExportConfig darwinCoreConfig;
  private final DarwinCoreContextBuilder contextBuilder;
  private final DarwinCoreMapper darwinCoreMapper;
  private final DarwinCoreMetaXmlGenerator metaXmlGenerator;

  // Set by generateOccurrenceCsv; written to by processEntity
  private TabularOutput<UUID, Map<String, String>> csvOutput;

  public DarwinCoreExportGenerator(
    DataExportStatusService dataExportStatusService,
    DataExportConfig dataExportConfig,
    Configuration jsonPathConfiguration,
    ElasticSearchDataSource elasticSearchDataSource,
    ObjectMapper objectMapper,
    DinaMessageProducer messageProducer,
    DarwinCoreExportConfig darwinCoreConfig,
    DarwinCoreContextBuilder contextBuilder,
    DarwinCoreMapper darwinCoreMapper,
    DarwinCoreMetaXmlGenerator metaXmlGenerator
    ) {

    super(
      dataExportStatusService,
      dataExportConfig,
      jsonPathConfiguration,
      elasticSearchDataSource,
      objectMapper,
      messageProducer);

    this.darwinCoreConfig = darwinCoreConfig;
    this.contextBuilder = contextBuilder;
    this.darwinCoreMapper = darwinCoreMapper;
    this.metaXmlGenerator = metaXmlGenerator;
  }

  @Override
  public String generateFilename(DataExport dinaExport) {
    return "occurrence.zip";
  }
  @Override
  protected void postRecordWrite(DataExport dinaExport, Path exportPath) throws IOException {
    Path metaXmlPath = exportPath.resolveSibling("meta.xml");
    metaXmlGenerator.generateMetaXml(metaXmlPath);
  }
  /**
   * Override processEntity to handle DarwinCore-specific logic
   *
   * Instead of flattening relationships into the entity,
   * we extract organism from the JSON:API document and build DwC records
   */
  @Override
  protected void processEntity(JsonNode entity, String fallbackId,
                               JsonNode relationshipSource,
                               Map<String, DataExportFunction> functions,
                               DataOutput<UUID, JsonNode> output) throws IOException {
    if (entity == null) {
      return;
    }

    String entityId = extractText(entity, JSONApiDocumentStructure.ID, fallbackId);
    if (StringUtils.isBlank(entityId)) {
      return;
    }

    // Build entity context map
    Map<String, JsonNode> entitiesContext = contextBuilder.buildContextMap(entity);

    // Build DwC record
    Map<String, String> dwcRecord = buildDwcRecord(entitiesContext);

    // Write to output
    if (csvOutput != null) {
      csvOutput.addRecord(UUID.fromString(entityId), dwcRecord);
    }
  }

  /**
   * @param entities    denormalized JSON:API entities (relationships embedded as direct fields)
   * @param outputPath  path to write the CSV file to
   */
  public void generateOccurrenceCsv(List<JsonNode> entities, Path outputPath) throws IOException {
    List<String> headers = darwinCoreConfig.getOccurrence().getColumns().stream()
        .map(DarwinCoreExportConfig.ColumnMapping::getDwcTerm)
        .toList();

    try (FileWriter writer = new FileWriter(outputPath.toFile(), StandardCharsets.UTF_8);
         TabularOutput<UUID, Map<String, String>> out = TabularOutput.create(
           TabularOutput.TabularOutputArgs.builder()
             .headers(headers)
             .columnSeparator(TabularOutput.ColumnSeparator.COMMA)
             .build(),
           MAP_STRING_TYPEREF, writer)) {
      this.csvOutput = out;
      try {
        for (JsonNode entity : entities) {
          processEntity(entity, null, null, null, null);
        }
      } finally {
        this.csvOutput = null;
      }
    }
  }

  /**
   * Build DarwinCore record from entity context
   */
  private Map<String, String> buildDwcRecord(Map<String, JsonNode> entitiesContext) {
    Map<String, String> record = new LinkedHashMap<>();
    List<DarwinCoreExportConfig.ColumnMapping> columnMappings = darwinCoreConfig.getOccurrence().getColumns();

    for (DarwinCoreExportConfig.ColumnMapping mapping : columnMappings) {
      String value = darwinCoreMapper.extractValue(entitiesContext, mapping);
      record.put(mapping.getDwcTerm(), value != null ? value : "");
    }

    return record;
  }
}

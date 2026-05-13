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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DarwinCoreExportGenerator extends RecordBasedExportGenerator {

  private final DarwinCoreExportConfig darwinCoreConfig;
  private final DarwinCoreContextBuilder contextBuilder;
  private final DarwinCoreMapper darwinCoreMapper;

  public DarwinCoreExportGenerator(
    DataExportStatusService dataExportStatusService,
    DataExportConfig dataExportConfig,
    Configuration jsonPathConfiguration,
    ElasticSearchDataSource elasticSearchDataSource,
    ObjectMapper objectMapper,
    DinaMessageProducer messageProducer,
    DarwinCoreExportConfig darwinCoreConfig,
    DarwinCoreContextBuilder contextBuilder,
    DarwinCoreMapper darwinCoreMapper
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
  }

  @Override
  public String generateFilename(DataExport dinaExport) {
    return "occurrence.csv";
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
    // TODO using TabularOutput
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

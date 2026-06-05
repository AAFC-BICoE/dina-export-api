package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.entity.DataExportSchemaEntry;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreContextBuilder;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreMapper;
import ca.gc.aafc.dina.export.api.generator.helper.RelationshipFlattener;
import ca.gc.aafc.dina.export.api.output.DataOutput;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;
import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.messaging.producer.DinaMessageProducer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DarwinCoreExportGenerator extends RecordBasedExportGenerator {

  private final ObjectMapper objectMapper;
  private final DarwinCoreExportConfig darwinCoreConfig;
  private final DarwinCoreContextBuilder contextBuilder;
  private final DarwinCoreMapper darwinCoreMapper;
  private final DarwinCoreMetaXmlGenerator metaXmlGenerator;

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

    this.objectMapper = objectMapper;
    this.darwinCoreConfig = darwinCoreConfig;
    this.contextBuilder = contextBuilder;
    this.darwinCoreMapper = darwinCoreMapper;
    this.metaXmlGenerator = metaXmlGenerator;
  }

  @Override
  public String generateFilename(DataExport dinaExport) {
    return "dwca.zip";
  }

  // TODO overwrite to return DarwinCore headers
  protected LinkedHashMap<String, DataExportSchemaEntry> getEffectiveSchema(DataExport dinaExport) {
    return MapUtils.isNotEmpty(dinaExport.getSchema()) ? dinaExport.getSchema() : new LinkedHashMap<>();
  }

  @Override
  protected void exportSingleEntity(DataExport dinaExport,
                                    LinkedHashMap<String, DataExportSchemaEntry> schema,
                                    Path exportPath) throws IOException {
    queryAndProcess(dinaExport, null, false);
  }

  @Override
  protected void postRecordWrite(DataExport dinaExport, Path exportPath) throws IOException {
    metaXmlGenerator.generateMetaXml(exportWorkDir.get().resolve("meta.xml"));
    super.postRecordWrite(dinaExport, exportPath);
  }

  @Override
  public void deleteExport(DataExport dinaExport) throws IOException {
    if (dinaExport.getExportType() != DataExport.ExportType.DWCA) {
      throw new IllegalArgumentException("Should only be used for ExportType DWCA");
    }
    doDeleteExport(dinaExport);
  }

  @Override
  protected void processEntity(JsonNode entity, String fallbackId,
                               JsonNode relationshipSource,
                               Map<String, DataExportFunction> functions,
                               DataOutput<UUID, JsonNode> output) throws IOException {
    if (entity == null) {
      return;
    }

    // Flatten to attributes + embedded included relationships before context navigation
    var denormalized = RelationshipFlattener.toFlatEntity(objectMapper, entity, relationshipSource);

    String entityId = extractText(denormalized, JSONApiDocumentStructure.ID, fallbackId);
    if (StringUtils.isBlank(entityId)) {
      return;
    }

    Map<String, JsonNode> entitiesContext = contextBuilder.buildContextMap(denormalized);
    Map<String, String> dwcRecord = buildDwcRecord(entitiesContext);

    // TODO set the type
    output.addRecord(type, UUID.fromString(entityId), dwcRecord);
  }

  private Map<String, String> buildDwcRecord(Map<String, JsonNode> entitiesContext) {
    Map<String, String> record = new LinkedHashMap<>();
    List<DarwinCoreExportConfig.ColumnMapping> columnMappings = darwinCoreConfig.getCore().getColumns();

    for (DarwinCoreExportConfig.ColumnMapping mapping : columnMappings) {
      String value = darwinCoreMapper.extractValue(entitiesContext, mapping);
      record.put(mapping.getDwcTerm(), value != null ? value : "");
    }

    return record;
  }
}

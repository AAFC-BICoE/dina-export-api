package ca.gc.aafc.dina.export.api.generator;

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
import ca.gc.aafc.dina.export.api.generator.dwc.DarwinCoreEmlGenerator;
import ca.gc.aafc.dina.export.api.generator.dwc.DarwinCoreMapper;
import ca.gc.aafc.dina.export.api.generator.dwc.EmlMapper;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreContextBuilder;
import ca.gc.aafc.dina.export.api.output.DataOutput;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;
import ca.gc.aafc.dina.export.api.service.ExternalRelationshipResolver;
import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.messaging.producer.DinaMessageProducer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DarwinCoreExportGenerator extends RecordBasedExportGenerator {

  private final ObjectMapper objectMapper;
  private final DarwinCoreExportConfig darwinCoreConfig;
  private final DarwinCoreContextBuilder contextBuilder;
  private final DarwinCoreMapper darwinCoreMapper;
  private final DarwinCoreMetaXmlGenerator metaXmlGenerator;
  private final DarwinCoreEmlGenerator emlGenerator;
  private final EmlMapper emlMapper;

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
    DarwinCoreMetaXmlGenerator metaXmlGenerator,
    DarwinCoreEmlGenerator emlGenerator,
    EmlMapper emlMapper,
    ExternalRelationshipResolver externalRelationshipResolver
  ) {

    super(
      dataExportStatusService,
      dataExportConfig,
      jsonPathConfiguration,
      elasticSearchDataSource,
      objectMapper,
      messageProducer,
      externalRelationshipResolver);

    this.objectMapper = objectMapper;
    this.darwinCoreConfig = darwinCoreConfig;
    this.contextBuilder = contextBuilder;
    this.darwinCoreMapper = darwinCoreMapper;
    this.metaXmlGenerator = metaXmlGenerator;
    this.emlGenerator = emlGenerator;
    this.emlMapper = emlMapper;
  }

  @Override
  public String generateFilename(DataExport dinaExport) {
    return "dwca.zip";
  }

  @Override
  protected LinkedHashMap<String, DataExportSchemaEntry> getEffectiveSchema(DataExport dinaExport) {
    List<String> headers = darwinCoreConfig.getCore().getColumns().stream()
      .map(DarwinCoreExportConfig.ColumnMapping::getDwcTerm)
      .toList();

    LinkedHashMap<String, DataExportSchemaEntry> schema = new LinkedHashMap<>();
    schema.put(darwinCoreConfig.getCore().getEntityType(), new DataExportSchemaEntry(headers, null));
    return schema;
  }

  /**
   * Relationship paths of the configured resource contexts, from the root.
   * e.g. a "collectors" context with path "collectors" under "collectingEvent" -> collectingEvent.collectors
   */
  @Override
  protected Set<String> getExternalRelationshipPaths(DataExport dinaExport) {
    Map<String, String> pathByContext = new HashMap<>();
    Map<String, DarwinCoreExportConfig.ResourceContext> resourceContexts = darwinCoreConfig.getCore().getResourceContexts();
    if (resourceContexts != null) {
      resourceContexts.forEach((name, context) -> {
        if (!context.isRoot()) {
          String parentPath = pathByContext.get(context.getContext());
          pathByContext.put(name, parentPath == null ? context.getPath() : parentPath + "." + context.getPath());
        }
      });
    }
    return new HashSet<>(pathByContext.values());
  }

  @Override
  protected void postRecordWrite(DataExport dinaExport, RecordExportContext ctx) throws IOException {
    Path workDir = ctx.exportWorkDir();
    metaXmlGenerator.generateMetaXml(workDir.resolve(DarwinCoreMetaXmlGenerator.DEFAULT_META_FILENAME));
    if (dinaExport.getDataset() != null) {
      emlGenerator.generateEml(workDir.resolve(DarwinCoreEmlGenerator.DEFAULT_EML_FILENAME),
        emlMapper.datasetToEml(dinaExport.getDataset(), dinaExport.getName()));
    }

    super.postRecordWrite(dinaExport, ctx);
  }

  @Override
  public void deleteExport(DataExport dinaExport) throws IOException {
    if (dinaExport.getExportType() != DataExport.ExportType.DWCA) {
      throw new IllegalArgumentException("Should only be used for ExportType DWCA");
    }
    doDeleteExport(dinaExport);
  }

  @Override
  protected void processIncluded(JsonNode entity, Map<String, DataExportFunction> functions,
                                  DataOutput<UUID, JsonNode> output) {
    // DWCA accesses /included entities via RelationshipFlattener, not as separate rows
  }

  @Override
  protected void processEntity(JsonNode entity, String fallbackId,
                               JsonNode relationshipSource,
                               Map<String, DataExportFunction> functions,
                               DataOutput<UUID, JsonNode> output,
                               Set<String> externalRelationshipPaths) throws IOException {
    if (entity == null) {
      return;
    }

    // Flatten to attributes + embedded included relationships before context navigation
    var denormalized = newRelationshipFlattener(externalRelationshipPaths).toFlatEntity(entity, relationshipSource);

    String entityId = extractText(denormalized, JSONApiDocumentStructure.ID, fallbackId);
    if (StringUtils.isBlank(entityId)) {
      return;
    }

    Map<String, JsonNode> entitiesContext = contextBuilder.buildContextMap(denormalized);
    Map<String, String> dwcRecord = buildDwcRecord(entitiesContext);

    output.addRecord(darwinCoreConfig.getCore().getEntityType(), UUID.fromString(entityId), objectMapper.valueToTree(dwcRecord));
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

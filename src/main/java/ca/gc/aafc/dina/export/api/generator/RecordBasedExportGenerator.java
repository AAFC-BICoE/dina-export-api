package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.export.api.config.DataExportOption;
import ca.gc.aafc.dina.export.api.config.UserNotificationQueueProperties;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.entity.DataExportSchemaEntry;
import ca.gc.aafc.dina.export.api.generator.helper.ExportFunctionHandler;
import ca.gc.aafc.dina.export.api.generator.helper.RelationshipFlattener;
import ca.gc.aafc.dina.export.api.output.ZipPackager;
import ca.gc.aafc.dina.export.api.output.CompositeDataOutput;
import ca.gc.aafc.dina.export.api.output.DataOutput;
import ca.gc.aafc.dina.export.api.output.TabularOutput;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;
import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;
import ca.gc.aafc.dina.json.JsonHelper;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.messaging.message.UserMessageNotification;
import ca.gc.aafc.dina.messaging.producer.DinaMessageProducer;

import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.MAP_TYPEREF;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.log4j.Log4j2;

/**
 * Generates tabular (CSV/TSV) exports
 *
 * Supports single-resource exports (one CSV file) and multi-resource exports
 * (multiple CSVs packaged in a ZIP). The output layer ({@link DataOutput} / {@link CompositeDataOutput})
 * handles record routing and type filtering.
 */
@Service
@Log4j2
public class RecordBasedExportGenerator extends DataExportGenerator {

  private final ObjectMapper objectMapper;
  private final ElasticSearchDataSource elasticSearchDataSource;
  private final DataExportConfig dataExportConfig;
  private final Configuration jsonPathConfiguration;
  private final DinaMessageProducer messageProducer;

  public RecordBasedExportGenerator(
    DataExportStatusService dataExportStatusService,
    DataExportConfig dataExportConfig,
    Configuration jsonPathConfiguration,
    ElasticSearchDataSource elasticSearchDataSource,
    ObjectMapper objectMapper,
    DinaMessageProducer messageProducer) {

    super(dataExportStatusService);

    this.elasticSearchDataSource = elasticSearchDataSource;
    this.objectMapper = objectMapper;
    this.dataExportConfig = dataExportConfig;
    this.jsonPathConfiguration = jsonPathConfiguration;
    this.messageProducer = messageProducer;
  }

  @Override
  public String generateFilename(DataExport dinaExport) {
    LinkedHashMap<String, DataExportSchemaEntry> schema = getEffectiveSchema(dinaExport);

    if (isMultiEntityExport(dinaExport, schema)) {
      return dinaExport.getUuid().toString() + ZipPackager.EXTENSION;
    }

    String separator = getColumnSeparatorOption(dinaExport);
    return DataExportConfig.DATA_EXPORT_TABULAR_FILENAME + TabularOutput.extensionFromSeparator(separator);
  }

  @Async(DataExportConfig.DINA_THREAD_POOL_BEAN_NAME)
  @Override
  public CompletableFuture<UUID> export(DataExport dinaExport) throws IOException {
    DataExport.ExportStatus currStatus = waitForRecord(dinaExport.getUuid());

    if (DataExport.ExportStatus.NEW != currStatus) {
      log.error("Unexpected DataExport status: {}", currStatus);
      return CompletableFuture.completedFuture(dinaExport.getUuid());
    }

    Path exportPath = dataExportConfig.getPathForDataExport(dinaExport).orElse(null);
    if (exportPath == null) {
      log.error("Null export path");
      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
      return CompletableFuture.completedFuture(dinaExport.getUuid());
    }

    updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.RUNNING);
    try {
      ensureDirectoryExists(exportPath.getParent());
      LinkedHashMap<String, DataExportSchemaEntry> schema = getEffectiveSchema(dinaExport);

      if (isMultiEntityExport(dinaExport, schema)) {
        exportMultiEntity(dinaExport, schema, exportPath);
      } else {
        exportSingleEntity(dinaExport, schema, exportPath);
      }
      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.COMPLETED);
      messageProducer.send(buildUserMessageNotification(dinaExport));
    } catch (IOException ioEx) {
      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
      throw ioEx;
    }

    return CompletableFuture.completedFuture(dinaExport.getUuid());
  }

  private UserMessageNotification buildUserMessageNotification(DataExport dinaExport) {
    return UserMessageNotification
      .builder()
      .username(dinaExport.getCreatedBy())
      .title("Data Export Ready")
      .notificationType(UserNotificationQueueProperties.NotificationType.DATA_EXPORT_READY.name())
      .notificationParams(Map.of("id", dinaExport.getUuid().toString()))
      .build();
  }

  @Override
  public void deleteExport(DataExport dinaExport) throws IOException {
    if (dinaExport.getExportType() != DataExport.ExportType.TABULAR_DATA) {
      throw new IllegalArgumentException("Should only be used for ExportType TABULAR_DATA");
    }

    Path exportPath = dataExportConfig.getPathForDataExport(dinaExport).orElse(null);
    deleteIfExists(exportPath);

    if (exportPath != null
      && DataExportConfig.isExportTypeUsesDirectory(DataExport.ExportType.TABULAR_DATA)
      && DataExportConfig.isDataExportDirectory(exportPath.getParent(), dinaExport)) {
      deleteIfExists(exportPath.getParent());
    }
  }

  // Export single/multi entity

  private void exportSingleEntity(DataExport dinaExport, LinkedHashMap<String, DataExportSchemaEntry> schema,
                                   Path exportPath) throws IOException {
    try (Writer writer = new FileWriter(exportPath.toFile(), StandardCharsets.UTF_8);
         TabularOutput<UUID, JsonNode> output =
           TabularOutput.create(buildOutputArgs(dinaExport, schema), new TypeReference<>() { }, writer)) {
      queryAndProcess(dinaExport, output, false);
    }
  }

  private void exportMultiEntity(DataExport dinaExport, LinkedHashMap<String, DataExportSchemaEntry> schema,
                                  Path exportPath) throws IOException {
    Path tempDir = Files.createTempDirectory("dina-export-" + dinaExport.getUuid());
    try {
      String fileExtension = TabularOutput.extensionFromSeparator(getColumnSeparatorOption(dinaExport));
      Map<String, TabularOutput<UUID, JsonNode>> outputsByType = new HashMap<>();
      Map<String, Writer> writersByType = new HashMap<>();

      for (var entry : schema.entrySet()) {
        String entityType = entry.getKey();
        Writer writer = new FileWriter(
          tempDir.resolve(entityType + fileExtension).toFile(), StandardCharsets.UTF_8);
        writersByType.put(entityType, writer);

        TabularOutput.TabularOutputArgs args = buildOutputArgsForEntity(
          dinaExport, entityType, entry.getValue());
        outputsByType.put(entityType, TabularOutput.create(args, new TypeReference<>() { }, writer));
      }

      try (CompositeDataOutput<UUID, JsonNode> composite = new CompositeDataOutput<>(outputsByType)) {
        queryAndProcess(dinaExport, composite, true);
      }

      for (Writer writer : writersByType.values()) {
        writer.close();
      }

      ZipPackager.createZipPackage(tempDir, exportPath);
    } finally {
      ZipPackager.deleteDirectoryRecursively(tempDir);
    }
  }

  // query + record processing

  /**
   * Pages through Elasticsearch results and writes each entity to the output.
   *
   * Both modes parse /included data from JSON:API responses:
   * - Single-entity: /included data is merged into primary entity via RelationshipFlattener (denormalized)
   * - Multi-entity: /included entities are also processed as separate rows in separate CSVs (normalized)
   *
   * @param isMultiEntity if true, each /included entity becomes its own row in addition to merging
   */
  private void queryAndProcess(DataExport dinaExport, DataOutput<UUID, JsonNode> output,
                                boolean isMultiEntity) throws IOException {
    String query = objectMapper.writeValueAsString(dinaExport.getQuery());
    Map<String, DataExportFunction> functions = dinaExport.getFunctions();
    
    // Merge relationships when schema includes multiple entity types
    boolean needsRelationships = isMultiEntity || getEffectiveSchema(dinaExport).size() > 1;

    SearchResponse<JsonNode> response =
      elasticSearchDataSource.searchWithPIT(dinaExport.getSource(), query);

    try {
      boolean pageAvailable = !response.hits().hits().isEmpty();
      while (pageAvailable) {
        for (Hit<JsonNode> hit : response.hits().hits()) {
          processHit(hit, functions, output, isMultiEntity, needsRelationships);
        }

        int hitCount = response.hits().hits().size();
        if (elasticSearchDataSource.getPageSize() == hitCount) {
          Hit<JsonNode> lastHit = response.hits().hits().get(hitCount - 1);
          response = elasticSearchDataSource.searchAfter(query, response.pitId(), lastHit.sort());
        } else {
          pageAvailable = false;
        }
      }
    } finally {
      elasticSearchDataSource.closePointInTime(response.pitId());
    }
  }

  private void processHit(Hit<JsonNode> hit, Map<String, DataExportFunction> functions,
                           DataOutput<UUID, JsonNode> output, boolean isMultiEntity, 
                           boolean needsRelationships) throws IOException {
    JsonNode source = hit.source();
    if (source == null) {
      return;
    }

    Optional<JsonNode> dataOpt = JsonHelper.atJsonPtr(source, JSONApiDocumentStructure.DATA_PTR);
    if (dataOpt.isEmpty()) {
      return;
    }

    // Main /data entity — merge relationships
    processEntity(dataOpt.get(), hit.id(), source, functions, output);

    // Multi-entity mode: also process each /included entity as its own separate row
    if (isMultiEntity) {
      Optional<JsonNode> includedOpt = JsonHelper.atJsonPtr(source, JSONApiDocumentStructure.INCLUDED_PTR);
      if (includedOpt.isPresent() && includedOpt.get().isArray()) {
        for (JsonNode entity : includedOpt.get()) {
          processEntity(entity, null, null, functions, output);
        }
      }
    }
  }

  /**
   * Transforms a single JSON:API entity node and writes it to the output.
   * The output layer decides whether to accept or skip based on entity type.
   */
  private void processEntity(JsonNode entity, String fallbackId, 
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

    // Attributes processing
    JsonNode attrsNode = entity.get(JSONApiDocumentStructure.ATTRIBUTES);
    if (attrsNode == null || attrsNode.isNull()) {
      return;
    }

    ObjectNode attributes = attrsNode.deepCopy();
    attributes.put(JSONApiDocumentStructure.ID, entityId);

    // 1. Merge Relationships if source exists
    if (relationshipSource != null) {
      RelationshipFlattener relationshipFlattener = new RelationshipFlattener(objectMapper, jsonPathConfiguration);
      relationshipFlattener.mergeRelationshipsIntoAttributes(relationshipSource, attributes);
    }

    // 2. Flatten nested objects (e.g. managedAttributes) to dot notation
    flattenNestedMaps(attributes); 

    ExportFunctionHandler.applyExportFunctions(attributes, functions);

    String type = extractText(entity, JSONApiDocumentStructure.TYPE, "");
    output.addRecord(type, UUID.fromString(entityId), attributes);
  }

  // Helpers

  private void flattenNestedMaps(ObjectNode node) {
    var result = JSONApiDocumentStructure.extractNestedMapUsingDotNotation(
        objectMapper.convertValue(node, MAP_TYPEREF));
    
    result.nestedMapsMap().forEach((k, v) -> node.set(k, objectMapper.valueToTree(v)));
    result.usedKeys().forEach(node::remove);
  }
  
  private static LinkedHashMap<String, DataExportSchemaEntry> getEffectiveSchema(DataExport dinaExport) {
    return MapUtils.isNotEmpty(dinaExport.getSchema()) ? dinaExport.getSchema() : new LinkedHashMap<>();
  }

  private static String getColumnSeparatorOption(DataExport dinaExport) {
    return MapUtils.isNotEmpty(dinaExport.getExportOptions())
      ? dinaExport.getExportOptions().get(DataExportOption.OPTION_COLUMN_SEPARATOR)
      : null;
  }

  private boolean isMultiEntityExport(DataExport dinaExport, LinkedHashMap<String, DataExportSchemaEntry> schema) {
    // Only create separate files (ZIP) if enablePackaging is true AND there are multiple entities
    boolean packagingEnabled = DataExportOption.getOptionAsBool(dinaExport.getExportOptions(),
      DataExportOption.ENABLE_PACKAGING);
    return MapUtils.isNotEmpty(schema) && schema.size() > 1 && packagingEnabled;
  }

  private TabularOutput.TabularOutputArgs buildOutputArgs(DataExport dinaExport,
                                                          LinkedHashMap<String, DataExportSchemaEntry> schema) {
    if (schema.isEmpty()) {
      throw new IllegalArgumentException("Schema cannot be empty");
    }

    // Identify Primary vs Related
    List<String> allColumns = new ArrayList<>();
    List<String> allAliases = new ArrayList<>();

    var iterator = schema.entrySet().iterator();

    // Handle primary entity (first entry in schema)
    var primaryEntry = iterator.next();
    DataExportSchemaEntry primarySchema = primaryEntry.getValue();

    allColumns.addAll(primarySchema.columns());
    addAliasesFromSchema(primarySchema, allAliases);

    // Handle Related Columns (Remaining entries)
    while (iterator.hasNext()) {
      var entry = iterator.next();
      DataExportSchemaEntry entitySchema = entry.getValue();
      String prefix = entry.getKey() + ".";

      for (String col : entitySchema.columns()) {
        allColumns.add(prefix + col);
      }
      addAliasesFromSchema(entitySchema, allAliases);
    }

    // Pass the fully constructed lists to the builder
    var builder = TabularOutput.TabularOutputArgs.builder()
      .headers(allColumns)
      .receivedHeadersAliases(allAliases);

    applyColumnSeparator(dinaExport, builder);
    
    return builder.build();
  }

  
  private TabularOutput.TabularOutputArgs buildOutputArgsForEntity(
    DataExport dinaExport, String entityType, DataExportSchemaEntry entitySchema) {

    var builder = TabularOutput.TabularOutputArgs.builder()
      .headers(entitySchema.columns())
      .receivedHeadersAliases(entitySchema.aliases())
      .enableIdTracking(true);  // Always enabled for multi-entity exports

    applyColumnSeparator(dinaExport, builder);
    return builder.build();
  }

  private static void applyColumnSeparator(DataExport dinaExport,
                                            TabularOutput.TabularOutputArgs.TabularOutputArgsBuilder builder) {
    String separator = getColumnSeparatorOption(dinaExport);
    if (separator != null) {
      TabularOutput.ColumnSeparator.fromString(separator)
        .filter(s -> s != TabularOutput.ColumnSeparator.COMMA)
        .ifPresent(builder::columnSeparator);
    }
  }

  // ── JSON transformation helpers ────────────────────────────────────────

  private static String extractText(JsonNode node, String field, String fallback) {
    JsonNode child = node.get(field);
    return child != null ? child.asText() : fallback;
  }

  /**
   * Add aliases from EntitySchema to the accumulator list.
   * Converts null aliases within the array to empty strings.
   * This maintains backward compatibility with the previous columnAliases behavior.
   * The EntitySchema constructor already validates that aliases length matches columns length.
   */
  private void addAliasesFromSchema(DataExportSchemaEntry schema, List<String> accumulator) {
    List<String> aliases = schema.aliases();
    int colCount = schema.columns().size();

    for (int i = 0; i < colCount; i++) {
      if (aliases != null && i < aliases.size()) {
        String alias = aliases.get(i);
        // Use empty string for null aliases to signal "use column name"
        accumulator.add(alias != null ? alias : "");
      } else {
        // No aliases provided - use empty string to signal "use column name"
        accumulator.add("");
      }
    }
  }

}


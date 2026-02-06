package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
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
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.generator.helper.ExportFunctionHandler;
import ca.gc.aafc.dina.export.api.generator.helper.RelationshipFlattener;
import ca.gc.aafc.dina.export.api.generator.helper.ZipPackager;
import ca.gc.aafc.dina.export.api.output.CompositeDataOutput;
import ca.gc.aafc.dina.export.api.output.DataOutput;
import ca.gc.aafc.dina.export.api.output.TabularOutput;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;
import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;
import ca.gc.aafc.dina.json.JsonHelper;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.log4j.Log4j2;

/**
 * Generates tabular (CSV/TSV) exports
 *
 * Supports single-entity exports (one CSV file) and multi-entity exports
 * (multiple CSVs packaged in a ZIP). The output layer ({@link DataOutput} / {@link CompositeDataOutput})
 * handles record routing and type filtering.
 */
@Service
@Log4j2
public class RecordBasedExportGenerator extends DataExportGenerator {

  private final ObjectMapper objectMapper;
  private final ElasticSearchDataSource elasticSearchDataSource;
  private final DataExportConfig dataExportConfig;
  private final RelationshipFlattener relationshipFlattener;

  public RecordBasedExportGenerator(
    DataExportStatusService dataExportStatusService,
    DataExportConfig dataExportConfig,
    Configuration jsonPathConfiguration,
    ElasticSearchDataSource elasticSearchDataSource,
    ObjectMapper objectMapper) {

    super(dataExportStatusService);

    this.elasticSearchDataSource = elasticSearchDataSource;
    this.objectMapper = objectMapper;
    this.dataExportConfig = dataExportConfig;
    this.relationshipFlattener = new RelationshipFlattener(objectMapper, jsonPathConfiguration);
  }

  @Override
  public String generateFilename(DataExport dinaExport) {
    Map<String, String[]> schema = getEffectiveSchema(dinaExport);

    if (isMultiEntityExport(dinaExport, schema)) {
      return dinaExport.getUuid().toString() + ".zip";
    }

    String separator = getColumnSeparatorOption(dinaExport);
    return DataExportConfig.DATA_EXPORT_TABULAR_FILENAME + extensionFromSeparator(separator);
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
      Map<String, String[]> schema = getEffectiveSchema(dinaExport);

      if (isMultiEntityExport(dinaExport, schema)) {
        exportMultiEntity(dinaExport, schema, exportPath);
      } else {
        exportSingleEntity(dinaExport, schema, exportPath);
      }
      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.COMPLETED);
    } catch (IOException ioEx) {
      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
      throw ioEx;
    }

    return CompletableFuture.completedFuture(dinaExport.getUuid());
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

  private void exportSingleEntity(DataExport dinaExport, Map<String, String[]> schema,
                                   Path exportPath) throws IOException {
    try (Writer writer = new FileWriter(exportPath.toFile(), StandardCharsets.UTF_8);
         TabularOutput<UUID, JsonNode> output =
           TabularOutput.create(buildOutputArgs(dinaExport, schema), new TypeReference<>() {}, writer)) {
      queryAndProcess(dinaExport, output, false);
    }
  }

  private void exportMultiEntity(DataExport dinaExport, Map<String, String[]> schema,
                                  Path exportPath) throws IOException {
    Path tempDir = Files.createTempDirectory("dina-export-" + dinaExport.getUuid());
    try {
      String fileExtension = extensionFromSeparator(getColumnSeparatorOption(dinaExport));
      Map<String, TabularOutput<UUID, JsonNode>> outputsByType = new HashMap<>();
      Map<String, Writer> writersByType = new HashMap<>();

      for (var entry : schema.entrySet()) {
        String entityType = entry.getKey();
        Writer writer = new FileWriter(
          tempDir.resolve(entityType + fileExtension).toFile(), StandardCharsets.UTF_8);
        writersByType.put(entityType, writer);

        TabularOutput.TabularOutputArgs args = buildOutputArgsForEntity(
          dinaExport, entityType, Arrays.asList(entry.getValue()), true);
        outputsByType.put(entityType, TabularOutput.create(args, new TypeReference<>() {}, writer));
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
   * @param isMultiEntity if true, /included entities are processed as separate records
   *                      and relationships are merged into the main /data entity
   */
  private void queryAndProcess(DataExport dinaExport, DataOutput<UUID, JsonNode> output,
                                boolean isMultiEntity) throws IOException {
    String query = objectMapper.writeValueAsString(dinaExport.getQuery());
    Map<String, DataExportFunction> functions = dinaExport.getFunctions();
    
    // For single-entity exports, check if schema has multiple entries (needs relationships merged)
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

    // Main /data entity — merge relationships when needed (multi-entity OR multi-entity schema)
    processEntity(dataOpt.get(), hit.id(), needsRelationships ? source : null, functions, output);

    // In multi-entity mode, each /included entity becomes its own row
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
  private void processEntity(JsonNode entity, String fallbackId, JsonNode relSource,
                              Map<String, DataExportFunction> functions,
                              DataOutput<UUID, JsonNode> output) throws IOException {
    if (entity == null) {
      return;
    }

    String entityId = extractText(entity, JSONApiDocumentStructure.ID, fallbackId);
    String entityType = extractText(entity, JSONApiDocumentStructure.TYPE, null);
    // Convert kebab-case to camelCase (e.g., "material-sample" -> "materialSample")
    entityType = kebabToCamelCase(entityType);

    if (StringUtils.isBlank(entityId)) {
      log.warn("Entity missing id, skipping");
      return;
    }

    JsonNode attributesNode = entity.get(JSONApiDocumentStructure.ATTRIBUTES);
    if (attributesNode == null || attributesNode.isNull() || !(attributesNode instanceof ObjectNode)) {
      log.warn("Entity {} has no attributes, skipping", entityId);
      return;
    }

    ObjectNode attributes = (ObjectNode) attributesNode.deepCopy();
    attributes.put(JSONApiDocumentStructure.ID, entityId);
    replaceNestedByDotNotation(attributes);

    if (relSource != null) {
      relationshipFlattener.mergeRelationshipsIntoAttributes(relSource, attributes);
      replaceNestedByDotNotation(attributes);
    }

    ExportFunctionHandler.applyExportFunctions(attributes, functions);

    output.addRecord(entityType, UUID.fromString(entityId), attributes);
  }

  // Helpers

  private static Map<String, String[]> getEffectiveSchema(DataExport dinaExport) {
    return MapUtils.isNotEmpty(dinaExport.getSchema()) ? dinaExport.getSchema() : Map.of();
  }

  private static String getColumnSeparatorOption(DataExport dinaExport) {
    return MapUtils.isNotEmpty(dinaExport.getExportOptions())
      ? dinaExport.getExportOptions().get(TabularOutput.OPTION_COLUMN_SEPARATOR)
      : null;
  }

  private static String extensionFromSeparator(String columnSeparator) {
    return "TAB".equals(columnSeparator) ? ".tsv" : ".csv";
  }

  private boolean isMultiEntityExport(DataExport dinaExport, Map<String, String[]> schema) {
    // Only create separate files (ZIP) if enablePackaging is true AND there are multiple entities
    boolean packagingEnabled = dinaExport.getExportOptions() != null && 
      Boolean.parseBoolean(dinaExport.getExportOptions().getOrDefault("enablePackaging", "false"));
    return MapUtils.isNotEmpty(schema) && schema.size() > 1 && packagingEnabled;
  }

  private TabularOutput.TabularOutputArgs buildOutputArgs(DataExport dinaExport,
                                                           Map<String, String[]> schema) {
    // For single-entity exports (no packaging), merge all columns from all schema entries
    // The schema keys represent entity types/relationships in the data    
    List<String> allColumns = new ArrayList<>();
    String primaryEntity = null;
    
    // Process schema entries
    int index = 0;
    for (Map.Entry<String, String[]> entry : schema.entrySet()) {
      String entityType = entry.getKey();
      String[] columns = entry.getValue();
      
      if (index == 0) {
        // First entity is primary - columns are used as-is
        primaryEntity = entityType;
        allColumns.addAll(Arrays.asList(columns));
      } else {
        // Related entities - prefix columns with entity type (relationship name from the data)
        for (String column : columns) {
          allColumns.add(entityType + "." + column);
        }
      }
      index++;
    }
    
    // Use primary entity for alias resolution (or first key if somehow null)
    String outputEntity = primaryEntity != null ? primaryEntity : schema.keySet().iterator().next();
    
    // ID tracking not needed for single-entity exports
    return buildOutputArgsForEntity(dinaExport, outputEntity, allColumns, false);
  }

  private TabularOutput.TabularOutputArgs buildOutputArgsForEntity(
    DataExport dinaExport, String entityType, List<String> headers, boolean enableIdTracking) {

    var builder = TabularOutput.TabularOutputArgs.builder()
      .headers(headers)
      .receivedHeadersAliases(getAliasesForEntity(dinaExport, entityType))
      .enableIdTracking(enableIdTracking);

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

  @SuppressWarnings("unchecked")
  private static List<String> getAliasesForEntity(DataExport dinaExport, String entityType) {
    if (dinaExport.getColumnAliases() == null) {
      return null;
    }
    if (dinaExport.getColumnAliases() instanceof Map<?, ?> aliasMap) {
      Object entityAliases = aliasMap.get(entityType);
      if (entityAliases instanceof List) {
        return (List<String>) entityAliases;
      } else if (entityAliases instanceof String[] arr) {
        return Arrays.asList(arr);
      }
    }
    return null;
  }

  // ── JSON transformation helpers ────────────────────────────────────────

  private void replaceNestedByDotNotation(ObjectNode node) {
    JSONApiDocumentStructure.ExtractNestedMapResult result =
      JSONApiDocumentStructure.extractNestedMapUsingDotNotation(
        objectMapper.convertValue(node, MAP_TYPEREF));

    for (var entry : result.nestedMapsMap().entrySet()) {
      node.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
    }
    for (String key : result.usedKeys()) {
      node.remove(key);
    }
  }

  private static String extractText(JsonNode node, String field, String fallback) {
    JsonNode child = node.get(field);
    return child != null ? child.asText() : fallback;
  }

  private static String kebabToCamelCase(String kebabCase) {
    if (StringUtils.isBlank(kebabCase) || !kebabCase.contains("-")) {
      return kebabCase;
    }
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;
    for (char c : kebabCase.toCharArray()) {
      if (c == '-') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }
}

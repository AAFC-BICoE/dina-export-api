package ca.gc.aafc.dina.export.api.generator;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.output.CompositeDataOutput;
import ca.gc.aafc.dina.export.api.output.DataOutput;
import ca.gc.aafc.dina.export.api.output.TabularOutput;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;
import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;
import ca.gc.aafc.dina.json.JsonHelper;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.LIST_MAP_TYPEREF;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.log4j.Log4j2;

/**
 * Responsible to generate normalized export files (multiple CSVs in a ZIP).
 * This generator dynamically detects entity types from column prefixes and creates
 * separate CSV files for each entity type (e.g., samples, projects, organisms).
 */
@Service
@Log4j2
public class RecordBasedExportGenerator extends DataExportGenerator {

  private static final String MAIN_ENTITY_NAME_OPTION = "mainEntityName";

  private final ObjectMapper objectMapper;
  private final ElasticSearchDataSource elasticSearchDataSource;
  private final DataExportConfig dataExportConfig;
  private final RecordBasedExportHelper recordHelper;

  public RecordBasedExportGenerator(
      DataExportStatusService dataExportStatusService,
      DataExportConfig dataExportConfig,
      ElasticSearchDataSource elasticSearchDataSource,
      ObjectMapper objectMapper,
      RecordBasedExportHelper recordHelper) {
    super(dataExportStatusService);
    this.elasticSearchDataSource = elasticSearchDataSource;
    this.objectMapper = objectMapper;
    this.dataExportConfig = dataExportConfig;
    this.recordHelper = recordHelper;
  }

  @Override
  public String generateFilename(DataExport dinaExport) {
    return dinaExport.getUuid() + ".zip";
  }

  /**
   * Main export method.
   */
  @Async(DataExportConfig.DINA_THREAD_POOL_BEAN_NAME)
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

      List<String> expandedColumns = new ArrayList<>(Arrays.asList(dinaExport.getColumns()));
      List<String> expandedAliases = dinaExport.getColumnAliases() != null ?
          new ArrayList<>(Arrays.asList(dinaExport.getColumnAliases())) : null;

      recordHelper.expandWildcardsIfNeeded(dinaExport, expandedColumns, expandedAliases);

      Path parentDir = exportPath.getParent();
      if (parentDir == null) {
        throw new IllegalStateException("Export path has no parent directory");
      }
      exportWithNormalization(dinaExport, parentDir, expandedColumns, expandedAliases);
      createZipFromCsvFiles(parentDir, dinaExport.getUuid());

      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.COMPLETED);
    } catch (IOException ioEx) {
      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
      throw ioEx;
    }

    return CompletableFuture.completedFuture(dinaExport.getUuid());
  }

  /**
   * Export with relationship normalization - dynamically creates CSV files for each detected entity type.
   */
  private void exportWithNormalization(DataExport dinaExport, Path exportDir,
                                        List<String> expandedColumns,
                                        List<String> expandedAliases) throws IOException {
    
    // Detect entity types from column prefixes
    Map<String, EntityTypeInfo> entityTypes = detectEntityTypes(expandedColumns, expandedAliases, dinaExport);
    
    // Ensure ID columns for related entities (adds to expandedColumns and EntityTypeInfo)
    ensureIdColumnsInEntityTypes(expandedColumns, expandedAliases, entityTypes);
    
    // Build entity configurations for CompositeDataOutput
    Map<String, CompositeDataOutput.EntityConfig> entityConfigs = buildEntityConfigs(entityTypes, dinaExport);
    
    // Track unique records for each related entity type
    Map<String, Map<String, Map<String, Object>>> uniqueRecordsByType = new LinkedHashMap<>();
    for (String type : entityTypes.keySet()) {
      if (!type.isEmpty()) { // Skip main entity
        uniqueRecordsByType.put(type, new LinkedHashMap<>());
      }
    }
    
    // Create composite output that routes to multiple CSV files
    try (CompositeDataOutput<JsonNode> output = new CompositeDataOutput<>(
        entityConfigs, new TypeReference<>() { }, exportDir)) {
      
      // Process all records
      paginateThroughResults(dinaExport.getSource(),
          objectMapper.writeValueAsString(dinaExport.getQuery()),
          hit -> processRecordWithNormalization(hit.id(), hit.source(), dinaExport.getFunctions(),
              output, entityTypes, uniqueRecordsByType));
      
      // Write unique related entity records
      writeUniqueRelatedRecords(output, uniqueRecordsByType, entityTypes);
      
      log.info("Normalized export completed: {} entity types, {} total unique related records", 
          entityTypes.size(), uniqueRecordsByType.values().stream().mapToInt(Map::size).sum());
    }
  }

  /**
   * Paginates through Elasticsearch results using PIT (Point in Time).
   */
  private void paginateThroughResults(String sourceIndex, String query, 
                                       RecordProcessor processor) throws IOException {
    SearchResponse<JsonNode> response = elasticSearchDataSource.searchWithPIT(sourceIndex, query);

    boolean pageAvailable = !response.hits().hits().isEmpty();
    while (pageAvailable) {
      for (Hit<JsonNode> hit : response.hits().hits()) {
        processor.process(hit);
      }
      pageAvailable = false;

      int numberOfHits = response.hits().hits().size();
      if (elasticSearchDataSource.getPageSize() == numberOfHits) {
        Hit<JsonNode> lastHit = response.hits().hits().get(numberOfHits - 1);
        response = elasticSearchDataSource.searchAfter(query, response.pitId(), lastHit.sort());
        pageAvailable = true;
      }
    }

    elasticSearchDataSource.closePointInTime(response.pitId());
  }

  @FunctionalInterface
  private interface RecordProcessor {
    void process(Hit<JsonNode> hit) throws IOException;
  }

  /**
   * Processes a single record for normalized export.
   */
  private void processRecordWithNormalization(String documentId, JsonNode record,
                                               Map<String, ca.gc.aafc.dina.export.api.config.DataExportFunction> columnFunctions,
                                               DataOutput<JsonNode> output,
                                               Map<String, EntityTypeInfo> entityTypes,
                                               Map<String, Map<String, Map<String, Object>>> uniqueRecordsByType) {
    if (record == null) {
      return;
    }

    ObjectNode attributeObjNode = recordHelper.prepareAttributeNode(documentId, record);
    if (attributeObjNode == null) {
      return;
    }

    recordHelper.applyFunctions(attributeObjNode, columnFunctions);

    // Extract and track related entities
    for (Map.Entry<String, EntityTypeInfo> entry : entityTypes.entrySet()) {
      String entityType = entry.getKey();
      EntityTypeInfo entityInfo = entry.getValue();
      if (!entityInfo.prefix.isEmpty()) { // Skip main entity (which has empty prefix)
        extractAndTrackRelatedEntities(record, entityType, entityInfo, 
            uniqueRecordsByType.get(entityType));
      }
    }

    // Write main entity record
    try {
      String mainEntityType = getMainEntityType(entityTypes);
      output.addRecord(mainEntityType, attributeObjNode);
    } catch (IOException e) {
      log.error("Error writing record", e);
    }
  }

  // ========== Helper Methods ==========

  /**
   * Strips the entity prefix from a column name.
   */
  private static String stripPrefix(String column, String prefix) {
    if (column.startsWith(prefix + ".")) {
      return column.substring(prefix.length() + 1);
    }
    return column;
  }

  /**
   * Extracts the prefix from a column name (e.g., "projects.name" -> "projects").
   */
  private static String extractPrefix(String column) {
    int dotIndex = column.indexOf('.');
    return dotIndex > 0 ? column.substring(0, dotIndex) : "";
  }

  /**
   * Detects all entity types from column prefixes.
   */
  private Map<String, EntityTypeInfo> detectEntityTypes(List<String> columns, 
                                                          List<String> aliases,
                                                          DataExport dinaExport) {
    Map<String, EntityTypeInfo> entityTypes = new LinkedHashMap<>();
    String mainEntityName = getMainEntityName(dinaExport);
    
    // Group columns by prefix
    Map<String, List<Integer>> columnsByPrefix = new LinkedHashMap<>();
    
    for (int i = 0; i < columns.size(); i++) {
      String column = columns.get(i);
      String prefix = extractPrefix(column);
      columnsByPrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(i);
    }
    
    // Build EntityTypeInfo for each prefix
    for (Map.Entry<String, List<Integer>> entry : columnsByPrefix.entrySet()) {
      String prefix = entry.getKey();
      List<Integer> indices = entry.getValue();
      
      String entityType = prefix.isEmpty() ? mainEntityName : prefix;
      String filename = entityType + ".csv";
      
      List<String> entityColumns = indices.stream()
          .map(i -> columns.get(i))
          .map(col -> prefix.isEmpty() ? col : stripPrefix(col, prefix))
          .collect(Collectors.toList());
      
      List<String> entityAliases = aliases == null ? null :
          indices.stream()
              .map(i -> i < aliases.size() ? aliases.get(i) : "")
              .collect(Collectors.toList());
      
      entityTypes.put(entityType, new EntityTypeInfo(
          entityType, prefix, filename, entityColumns, entityAliases, indices));
    }
    
    return entityTypes;
  }

  /**
   * Ensures ID columns for related entities in both the global columns list and EntityTypeInfo.
   */
  private void ensureIdColumnsInEntityTypes(List<String> columns, List<String> aliases,
                                 Map<String, EntityTypeInfo> entityTypes) {
    for (Map.Entry<String, EntityTypeInfo> entry : entityTypes.entrySet()) {
      EntityTypeInfo entityInfo = entry.getValue();
      String prefix = entityInfo.prefix;
      
      if (!prefix.isEmpty()) { // Only for related entities
        // Add to global columns list if not present
        String idColumn = prefix + ".id";
        if (!columns.contains(idColumn)) {
          columns.add(idColumn);
          if (aliases != null) {
            String aliasName = Character.toUpperCase(prefix.charAt(0)) + 
                prefix.substring(1) + " ID";
            aliases.add(aliasName);
          }
        }
        
        // Add "id" to the entity's own columns list if not present
        if (!entityInfo.columns.contains("id")) {
          entityInfo.columns.add(0, "id"); // Add at the beginning
          if (entityInfo.aliases != null && !entityInfo.aliases.isEmpty()) {
            String aliasName = Character.toUpperCase(prefix.charAt(0)) + 
                prefix.substring(1) + " ID";
            entityInfo.aliases.add(0, aliasName);
          }
        }
      }
    }
  }

  /**
   * Builds entity configurations for CompositeDataOutput.
   */
  /**
   * Builds entity configurations for CompositeDataOutput.
   * Extracts column separator from export options.
   */
  private Map<String, CompositeDataOutput.EntityConfig> buildEntityConfigs(
      Map<String, EntityTypeInfo> entityTypes, DataExport dinaExport) {
    
    Map<String, CompositeDataOutput.EntityConfig> configs = new LinkedHashMap<>();
    
    // Extract column separator from export options, default to COMMA
    TabularOutput.ColumnSeparator separator = TabularOutput.ColumnSeparator.COMMA;
    if (org.apache.commons.collections4.MapUtils.isNotEmpty(dinaExport.getExportOptions())) {
      String columnSeparator = dinaExport.getExportOptions().get(TabularOutput.OPTION_COLUMN_SEPARATOR);
      if (columnSeparator != null) {
        separator = TabularOutput.ColumnSeparator.fromString(columnSeparator)
            .orElse(TabularOutput.ColumnSeparator.COMMA);
      }
    }
    
    for (EntityTypeInfo info : entityTypes.values()) {
      configs.put(info.entityType, CompositeDataOutput.EntityConfig.builder()
          .filename(info.filename)
          .columns(info.columns)
          .aliases(info.aliases)
          .separator(separator)
          .build());
    }
    
    return configs;
  }

  /**
   * Gets the main entity type (the one without a prefix).
   */
  private String getMainEntityType(Map<String, EntityTypeInfo> entityTypes) {
    return entityTypes.values().stream()
        .filter(info -> info.prefix.isEmpty())
        .map(info -> info.entityType)
        .findFirst()
        .orElse("records");
  }

  /**
   * Gets the main entity name from export options or derives it from source.
   */
  private String getMainEntityName(DataExport dinaExport) {
    if (dinaExport.getExportOptions() != null) {
      String mainEntityName = dinaExport.getExportOptions().get(MAIN_ENTITY_NAME_OPTION);
      if (mainEntityName != null && !mainEntityName.isEmpty()) {
        return mainEntityName;
      }
    }
    // Default: derive from source (e.g., "material_sample_index" -> "samples")
    return "samples";
  }

  /**
   * Writes unique related entity records to output.
   */
  private void writeUniqueRelatedRecords(DataOutput<JsonNode> output,
                                          Map<String, Map<String, Map<String, Object>>> uniqueRecordsByType,
                                          Map<String, EntityTypeInfo> entityTypes) throws IOException {
    
    for (Map.Entry<String, Map<String, Map<String, Object>>> entry : uniqueRecordsByType.entrySet()) {
      String entityType = entry.getKey();
      Map<String, Map<String, Object>> uniqueRecords = entry.getValue();
      
      for (Map<String, Object> recordData : uniqueRecords.values()) {
        ObjectNode recordNode = objectMapper.createObjectNode();
        for (Map.Entry<String, Object> field : recordData.entrySet()) {
          recordNode.put(field.getKey(), field.getValue().toString());
        }
        output.addRecord(entityType, recordNode);
      }
    }
  }

  /**
   * Information about an entity type detected from columns.
   */
  private record EntityTypeInfo(
      String entityType,
      String prefix,
      String filename,
      List<String> columns,
      List<String> aliases,
      List<Integer> columnIndices
  ) { }

  /**
   * Creates a ZIP file from all CSV files in the directory and cleans up the originals.
   */
  @SuppressFBWarnings(
      value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
      justification = "Files from Files.list() always have valid filenames")
  private void createZipFromCsvFiles(Path exportDir, UUID exportId) throws IOException {
    Path zipPath = exportDir.resolve(exportId.toString() + ".zip");
    
    // Find all CSV files in the directory
    List<Path> csvFiles = Files.list(exportDir)
        .filter(path -> path.toString().endsWith(".csv"))
        .collect(Collectors.toList());
    
    if (csvFiles.isEmpty()) {
      log.warn("No CSV files found in {}", exportDir);
      return;
    }
    
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
      for (Path csvFile : csvFiles) {
        String fileName = csvFile.getFileName() != null 
            ? csvFile.getFileName().toString() 
            : csvFile.toString();
        addFileToZip(zos, csvFile, fileName);
      }
    }
    
    // Clean up CSV files
    for (Path csvFile : csvFiles) {
      Files.deleteIfExists(csvFile);
    }
    
    log.info("Created ZIP archive: {} with {} CSV files", zipPath, csvFiles.size());
  }

  private void addFileToZip(ZipOutputStream zos, Path filePath, String entryName) throws IOException {
    if (Files.exists(filePath)) {
      zos.putNextEntry(new ZipEntry(entryName));
      Files.copy(filePath, zos);
      zos.closeEntry();
    }
  }

  /**
   * Extracts related entity data from the JSON:API document using the helper's flatRelationships.
   * Works generically for any entity type (projects, organisms, collectors, etc.).
   */
  private void extractAndTrackRelatedEntities(JsonNode record, String entityType,
                                               EntityTypeInfo entityInfo,
                                               Map<String, Map<String, Object>> uniqueRecords) {
    
    // Check the JSON:API relationship structure to determine if it's to-one or to-many
    Optional<JsonNode> relNodeOpt = JsonHelper.atJsonPtr(record, JSONApiDocumentStructure.RELATIONSHIP_PTR);
    Optional<JsonNode> includedNodeOpt = JsonHelper.atJsonPtr(record, JSONApiDocumentStructure.INCLUDED_PTR);
    
    if (relNodeOpt.isEmpty() || includedNodeOpt.isEmpty()) {
      return;
    }
    
    JsonNode entityRel = relNodeOpt.get().get(entityType);
    if (entityRel == null || !entityRel.has(JSONApiDocumentStructure.DATA)) {
      return;
    }
    
    JsonNode entityData = entityRel.get(JSONApiDocumentStructure.DATA);
    if (entityData == null || entityData.isNull()) {
      return;
    }
    
    List<Map<String, Object>> includedDoc = objectMapper.convertValue(includedNodeOpt.get(), LIST_MAP_TYPEREF);
    
    // Handle to-many relationships (array of entities)
    if (entityData.isArray()) {
      entityData.elements().forEachRemaining(el -> {
        String entityId = el.get(JSONApiDocumentStructure.ID).asText();
        if (!uniqueRecords.containsKey(entityId)) {
          extractEntityFromIncluded(entityId, includedDoc, entityInfo, uniqueRecords);
        }
      });
    } else {
      // Handle to-one relationships (single entity object)
      String entityId = entityData.get(JSONApiDocumentStructure.ID).asText();
      if (!uniqueRecords.containsKey(entityId)) {
        extractEntityFromIncluded(entityId, includedDoc, entityInfo, uniqueRecords);
      }
    }
  }

  /**
   * Extracts an entity from the included section and adds it to unique records.
   */
  private void extractEntityFromIncluded(String entityId, List<Map<String, Object>> includedDoc,
                                          EntityTypeInfo entityInfo,
                                          Map<String, Map<String, Object>> uniqueRecords) {
    Map<String, Object> includedEntity = recordHelper.extractById(entityId, includedDoc);
    if (includedEntity.isEmpty()) {
      log.warn("Entity {} not found in included section", entityId);
      return;
    }
    
    @SuppressWarnings("unchecked")
    Map<String, Object> entityAttributes = (Map<String, Object>) includedEntity.get(JSONApiDocumentStructure.ATTRIBUTES);
    if (entityAttributes == null) {
      log.warn("No attributes found for entity {}", entityId);
      return;
    }
    
    Map<String, Object> flatAttributes = JSONApiDocumentStructure.mergeNestedMapUsingDotNotation(entityAttributes);
    extractEntityRecord(flatAttributes, entityInfo, entityId, uniqueRecords);
  }

  /**
   * Extracts requested columns from an entity record and adds it to the unique records map.
   */
  private void extractEntityRecord(Map<String, Object> flatAttributes, EntityTypeInfo entityInfo,
                                    String entityId, Map<String, Map<String, Object>> uniqueRecords) {
    Map<String, Object> recordData = new LinkedHashMap<>();
    recordData.put("id", entityId);

    // Extract requested columns for this entity
    for (String column : entityInfo.columns) {
      if (!"id".equals(column)) {
        Object value = getNestedValue(flatAttributes, column);
        if (value != null) {
          recordData.put(column, value);
        }
      }
    }

    uniqueRecords.put(entityId, recordData);
  }

  /**
   * Gets a nested value from a map using dot notation.
   * Handles cases like "extensionValues.mixs_soil_v5.project_name" where the map 
   * might be partially flattened (e.g., key "extensionValues.mixs_soil_v5" exists with value Map{project_name: "test"}).
   */
  private Object getNestedValue(Map<String, Object> map, String path) {
    // Try direct lookup first
    Object value = map.get(path);
    if (value != null) {
      return value;
    }
    
    // Try progressive lookup for nested paths
    String[] parts = path.split("\\.");
    for (int i = parts.length - 1; i > 0; i--) {
      String parentPath = String.join(".", Arrays.copyOfRange(parts, 0, i));
      Object parentValue = map.get(parentPath);
      
      if (parentValue instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> parentMap = (Map<String, Object>) parentValue;
        String remainingPath = String.join(".", Arrays.copyOfRange(parts, i, parts.length));
        return getNestedValue(parentMap, remainingPath);
      }
    }
    
    return null;
  }

  @Override
  public void deleteExport(DataExport dinaExport) throws IOException {
    if (dinaExport.getExportType() != DataExport.ExportType.TABULAR_DATA) {
      throw new IllegalArgumentException("Should only be used for ExportType TABULAR_DATA");
    }

    Path exportPath = dataExportConfig.getPathForDataExport(dinaExport).orElse(null);
    deleteIfExists(exportPath);

    if (exportPath != null &&
        DataExportConfig.isExportTypeUsesDirectory(DataExport.ExportType.TABULAR_DATA) &&
        DataExportConfig.isDataExportDirectory(exportPath.getParent(), dinaExport)) {
      deleteIfExists(exportPath.getParent());
    }
  }
}

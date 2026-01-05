package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.TypeRef;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.output.TabularOutput;
import ca.gc.aafc.dina.export.api.output.DataOutput;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;
import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;
import ca.gc.aafc.dina.json.JsonHelper;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.jsonapi.JsonPathHelper;

import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.LIST_MAP_TYPEREF;
import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.MAP_TYPEREF;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.log4j.Log4j2;

/**
 * Responsible to generate tabular export file.
 */
@Service
@Log4j2
public class TabularDataExportGenerator extends DataExportGenerator {

  private static final TypeRef<List<Map<String, Object>>> JSON_PATH_TYPE_REF = new TypeRef<>() { };

  public static final String NORMALIZE_RELATIONSHIPS_OPTION = "normalizeRelationships";
  private static final Set<String> PROJECT_PREFIXES = Set.of("projects.");

  private final ObjectMapper objectMapper;
  private final ElasticSearchDataSource elasticSearchDataSource;
  private final Configuration jsonPathConfiguration;
  private final DataExportConfig dataExportConfig;

  public TabularDataExportGenerator(
      DataExportStatusService dataExportStatusService,
      DataExportConfig dataExportConfig,
      Configuration jsonPathConfiguration, 
      ElasticSearchDataSource elasticSearchDataSource,
      ObjectMapper objectMapper) {
    super(dataExportStatusService);
    this.jsonPathConfiguration = jsonPathConfiguration;
    this.elasticSearchDataSource = elasticSearchDataSource;
    this.objectMapper = objectMapper;
    this.dataExportConfig = dataExportConfig;
  }

  @Override
  public String generateFilename(DataExport dinaExport) {
    // If normalizing relationships, output will be a ZIP file
    boolean normalizeRelationships = "true".equalsIgnoreCase(
        dinaExport.getExportOptions() != null ? 
        dinaExport.getExportOptions().get(NORMALIZE_RELATIONSHIPS_OPTION) : null);
    
    if (normalizeRelationships) {
      return dinaExport.getUuid() + ".zip";
    }
    
    TabularOutput.TabularOutputArgs args = createTabularOutputArgsFrom(dinaExport);
    return DataExportConfig.DATA_EXPORT_TABULAR_FILENAME + switch (args.getColumnSeparator()) {
      case TAB -> ".tsv";
      case COMMA -> ".csv";
      case null -> ".csv";
    };
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

      expandWildcardsIfNeeded(dinaExport, expandedColumns, expandedAliases);

      boolean normalizeRelationships = "true".equalsIgnoreCase(
          dinaExport.getExportOptions() != null ? 
          dinaExport.getExportOptions().get(NORMALIZE_RELATIONSHIPS_OPTION) : null);

      if (normalizeRelationships) {
        Path parentDir = exportPath.getParent();
        if (parentDir == null) {
          throw new IllegalStateException("Export path has no parent directory");
        }
        exportWithNormalization(dinaExport, parentDir, expandedColumns, expandedAliases);
        createZipFromCsvFiles(parentDir, dinaExport.getUuid());
      } else {
        exportStandard(dinaExport, exportPath, expandedColumns, expandedAliases);
      }

      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.COMPLETED);
    } catch (IOException ioEx) {
      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
      throw ioEx;
    }

    return CompletableFuture.completedFuture(dinaExport.getUuid());
  }

  /**
   * Expands wildcard columns if any are present.
   */
  private void expandWildcardsIfNeeded(DataExport dinaExport, List<String> columns, 
                                        List<String> aliases) throws IOException {
    boolean hasWildcards = columns.stream().anyMatch(col -> col.endsWith(".*"));
    if (hasWildcards) {
      WildcardColumnExpander expander = new WildcardColumnExpander(
          dinaExport.getSource(),
          objectMapper.writeValueAsString(dinaExport.getQuery()),
          columns,
          aliases,
          elasticSearchDataSource,
          objectMapper,
          this::flattenRecordRelationships);
      expander.expandWildcards();
    }
  }

  /**
   * Standard single CSV export.
   */
  private void exportStandard(DataExport dinaExport, Path exportPath,
                               List<String> expandedColumns, List<String> expandedAliases) throws IOException {
    DataExport expandedExport = DataExport.builder()
        .uuid(dinaExport.getUuid())
        .columns(expandedColumns.toArray(new String[0]))
        .columnAliases(expandedAliases != null ? expandedAliases.toArray(new String[0]) : null)
        .exportOptions(dinaExport.getExportOptions())
        .build();

    try (Writer w = new FileWriter(exportPath.toFile(), StandardCharsets.UTF_8);
         TabularOutput<JsonNode> output = TabularOutput.create(
             createTabularOutputArgsFrom(expandedExport), new TypeReference<>() { }, w)) {
      
      paginateThroughResults(dinaExport.getSource(), 
          objectMapper.writeValueAsString(dinaExport.getQuery()),
          hit -> processRecord(hit.id(), hit.source(), dinaExport.getFunctions(), output));
    }
  }

  /**
   * Export with relationship normalization - creates separate samples.csv and projects.csv files.
   */
  private void exportWithNormalization(DataExport dinaExport, Path exportDir,
                                        List<String> expandedColumns,
                                        List<String> expandedAliases) throws IOException {
    ensureProjectIdColumn(expandedColumns, expandedAliases);
    
    Map<String, List<Integer>> columnsByCategory = categorizeColumns(expandedColumns);
    List<Integer> sampleColumnIndices = columnsByCategory.get("samples");
    List<Integer> projectColumnIndices = columnsByCategory.get("projects");

    List<String> sampleColumns = extractColumns(expandedColumns, sampleColumnIndices, false);
    List<String> projectColumns = extractColumns(expandedColumns, projectColumnIndices, true);
    List<String> sampleAliases = extractAliases(expandedAliases, sampleColumnIndices);
    List<String> projectAliases = extractAliases(expandedAliases, projectColumnIndices);

    Map<String, Map<String, Object>> uniqueProjects = new LinkedHashMap<>();

    try (Writer samplesWriter = new FileWriter(exportDir.resolve("samples.csv").toFile(), StandardCharsets.UTF_8);
         Writer projectsWriter = projectColumns.isEmpty() ? null : 
             new FileWriter(exportDir.resolve("projects.csv").toFile(), StandardCharsets.UTF_8)) {

      TabularOutput<JsonNode> samplesOutput = createTabularOutput(sampleColumns, sampleAliases, 
          getColumnSeparator(dinaExport), samplesWriter);
      TabularOutput<JsonNode> projectsOutput = projectsWriter != null ? 
          createTabularOutput(projectColumns, projectAliases, getColumnSeparator(dinaExport), projectsWriter) : null;

      paginateThroughResults(dinaExport.getSource(),
          objectMapper.writeValueAsString(dinaExport.getQuery()),
          hit -> processRecordWithNormalization(hit.id(), hit.source(), dinaExport.getFunctions(),
              samplesOutput, projectColumnIndices, expandedColumns, uniqueProjects));

      samplesOutput.close();
      writeUniqueProjects(projectsOutput, uniqueProjects);
    }

    log.info("Normalized export completed: samples.csv, projects.csv ({} unique records)", uniqueProjects.size());
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
   * Prepares an attribute node by flattening nested maps and relationships.
   */
  private ObjectNode prepareAttributeNode(String documentId, JsonNode record) {
    Optional<JsonNode> attributes = JsonHelper.atJsonPtr(record, JSONApiDocumentStructure.ATTRIBUTES_PTR);
    if (attributes.isEmpty() || !(attributes.get() instanceof ObjectNode attributeObjNode)) {
      return null;
    }

    attributeObjNode.put(JSONApiDocumentStructure.ID, documentId);
    replaceNestedByDotNotation(attributeObjNode);

    Map<String, Object> flatRelationships = flatRelationships(record);
    Map<String, Object> flatRelationshipsDotNotation =
        JSONApiDocumentStructure.mergeNestedMapUsingDotNotation(flatRelationships);

    for (var entry : flatRelationshipsDotNotation.entrySet()) {
      attributeObjNode.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
      replaceNestedByDotNotation(attributeObjNode);
    }

    return attributeObjNode;
  }

  /**
   * Applies export functions (CONCAT, CONVERT_COORDINATES_DD) to the attribute node.
   */
  private void applyFunctions(ObjectNode attributeObjNode, Map<String, DataExportFunction> columnFunctions) {
    if (MapUtils.isEmpty(columnFunctions)) {
      return;
    }
    for (var functionDef : columnFunctions.entrySet()) {
      switch (functionDef.getValue().functionDef()) {
        case CONCAT -> attributeObjNode.put(functionDef.getKey(),
            handleConcatFunction(attributeObjNode, functionDef.getValue()));
        case CONVERT_COORDINATES_DD -> attributeObjNode.put(functionDef.getKey(),
            handleConvertCoordinatesDecimalDegrees(attributeObjNode, functionDef.getValue()));
        default -> log.warn("Unknown function. Ignoring");
      }
    }
  }

  /**
   * Processes a single record for standard export.
   */
  private void processRecord(String documentId, JsonNode record,
                              Map<String, DataExportFunction> columnFunctions,
                              DataOutput<JsonNode> output) {
    if (record == null) {
      return;
    }
    
    ObjectNode attributeObjNode = prepareAttributeNode(documentId, record);
    if (attributeObjNode == null) {
      return;
    }

    applyFunctions(attributeObjNode, columnFunctions);
    
    try {
      output.addRecord(attributeObjNode);
    } catch (IOException e) {
      log.error("Error writing record", e);
    }
  }

  /**
   * Processes a single record for normalized export.
   */
  private void processRecordWithNormalization(String documentId, JsonNode record,
                                               Map<String, DataExportFunction> columnFunctions,
                                               TabularOutput<JsonNode> samplesOutput,
                                               List<Integer> projectColumnIndices,
                                               List<String> allColumns,
                                               Map<String, Map<String, Object>> uniqueProjects) {
    if (record == null) {
      return;
    }

    ObjectNode attributeObjNode = prepareAttributeNode(documentId, record);
    if (attributeObjNode == null) {
      return;
    }

    applyFunctions(attributeObjNode, columnFunctions);

    if (!projectColumnIndices.isEmpty()) {
      extractAndTrackProjects(record, allColumns, projectColumnIndices, uniqueProjects);
    }

    try {
      samplesOutput.addRecord(attributeObjNode);
    } catch (IOException e) {
      log.error("Error writing record", e);
    }
  }

  /**
   * Flattens record relationships for use by WildcardColumnExpander.
   */
  private void flattenRecordRelationships(ObjectNode attributeObjNode, JsonNode record) {
    replaceNestedByDotNotation(attributeObjNode);
    Map<String, Object> flatRelationships = flatRelationships(record);
    Map<String, Object> flatRelationshipsDotNotation =
        JSONApiDocumentStructure.mergeNestedMapUsingDotNotation(flatRelationships);
    for (var entry : flatRelationshipsDotNotation.entrySet()) {
      attributeObjNode.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
      replaceNestedByDotNotation(attributeObjNode);
    }
  }

  // ========== Helper Methods ==========

  /**
   * Strips the "projects." prefix from a column name.
   */
  private static String stripProjectPrefix(String column) {
    for (String prefix : PROJECT_PREFIXES) {
      if (column.startsWith(prefix)) {
        return column.substring(prefix.length());
      }
    }
    return column;
  }

  /**
   * Ensures projects.id is included if there are project columns.
   */
  private void ensureProjectIdColumn(List<String> columns, List<String> aliases) {
    boolean hasProjectColumns = columns.stream()
        .anyMatch(col -> PROJECT_PREFIXES.stream().anyMatch(col::startsWith));
    
    if (hasProjectColumns && !columns.contains("projects.id")) {
      columns.add("projects.id");
      if (aliases != null) {
        aliases.add("Project ID");
      }
    }
  }

  /**
   * Categorizes columns into samples and projects based on their prefixes.
   */
  private Map<String, List<Integer>> categorizeColumns(List<String> columns) {
    List<Integer> sampleIndices = new ArrayList<>();
    List<Integer> projectIndices = new ArrayList<>();

    for (int i = 0; i < columns.size(); i++) {
      if (PROJECT_PREFIXES.stream().anyMatch(columns.get(i)::startsWith)) {
        projectIndices.add(i);
      } else {
        sampleIndices.add(i);
      }
    }
    return Map.of("samples", sampleIndices, "projects", projectIndices);
  }

  /**
   * Extracts columns at the given indices, optionally stripping project prefix.
   */
  private List<String> extractColumns(List<String> allColumns, List<Integer> indices, boolean stripPrefix) {
    return indices.stream()
        .map(allColumns::get)
        .map(col -> stripPrefix ? stripProjectPrefix(col) : col)
        .collect(Collectors.toList());
  }

  /**
   * Extracts aliases at the given indices.
   */
  private List<String> extractAliases(List<String> allAliases, List<Integer> indices) {
    if (allAliases == null) {
      return null;
    }
    return indices.stream()
        .map(i -> i < allAliases.size() ? allAliases.get(i) : "")
        .collect(Collectors.toList());
  }

  /**
   * Creates a TabularOutput with the given configuration.
   */
  private TabularOutput<JsonNode> createTabularOutput(List<String> columns, List<String> aliases,
                                                       TabularOutput.ColumnSeparator separator, 
                                                       Writer writer) throws IOException {
    return TabularOutput.create(
        TabularOutput.TabularOutputArgs.builder()
            .headers(columns)
            .receivedHeadersAliases(aliases)
            .columnSeparator(separator)
            .build(),
        new TypeReference<>() { }, writer);
  }

  /**
   * Writes unique projects to the output.
   */
  private void writeUniqueProjects(TabularOutput<JsonNode> projectsOutput,
                                    Map<String, Map<String, Object>> uniqueProjects) throws IOException {
    if (projectsOutput == null) {
      return;
    }

    for (Map<String, Object> projectData : uniqueProjects.values()) {
      ObjectNode projectNode = objectMapper.createObjectNode();
      for (var entry : projectData.entrySet()) {
        projectNode.put(entry.getKey(), entry.getValue().toString());
      }
      projectsOutput.addRecord(projectNode);
    }
    projectsOutput.close();
  }

  /**
   * Creates a ZIP file from CSV files and cleans up the originals.
   */
  private void createZipFromCsvFiles(Path exportDir, UUID exportId) throws IOException {
    Path zipPath = exportDir.resolve(exportId.toString() + ".zip");
    
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
      addFileToZip(zos, exportDir.resolve("samples.csv"), "samples.csv");
      addFileToZip(zos, exportDir.resolve("projects.csv"), "projects.csv");
    }
    
    Files.deleteIfExists(exportDir.resolve("samples.csv"));
    Files.deleteIfExists(exportDir.resolve("projects.csv"));
    
    log.info("Created ZIP archive: {}", zipPath);
  }

  private void addFileToZip(ZipOutputStream zos, Path filePath, String entryName) throws IOException {
    if (Files.exists(filePath)) {
      zos.putNextEntry(new ZipEntry(entryName));
      Files.copy(filePath, zos);
      zos.closeEntry();
    }
  }

  private static TabularOutput.TabularOutputArgs createTabularOutputArgsFrom(DataExport dinaExport) {
    List<String> headerAliases = dinaExport.getColumnAliases() != null ?
        Arrays.asList(dinaExport.getColumnAliases()) : null;

    var builder = TabularOutput.TabularOutputArgs.builder()
        .headers(Arrays.asList(dinaExport.getColumns()))
        .receivedHeadersAliases(headerAliases);

    if (MapUtils.isNotEmpty(dinaExport.getExportOptions())) {
      String columnSeparator = dinaExport.getExportOptions().get(TabularOutput.OPTION_COLUMN_SEPARATOR);
      if (columnSeparator != null) {
        TabularOutput.ColumnSeparator.fromString(columnSeparator)
            .filter(s -> s != TabularOutput.ColumnSeparator.COMMA)
            .ifPresent(builder::columnSeparator);
      }
    }
    return builder.build();
  }

  private TabularOutput.ColumnSeparator getColumnSeparator(DataExport dinaExport) {
    if (MapUtils.isNotEmpty(dinaExport.getExportOptions())) {
      String columnSeparator = dinaExport.getExportOptions().get(TabularOutput.OPTION_COLUMN_SEPARATOR);
      if (columnSeparator != null) {
        return TabularOutput.ColumnSeparator.fromString(columnSeparator)
            .orElse(TabularOutput.ColumnSeparator.COMMA);
      }
    }
    return TabularOutput.ColumnSeparator.COMMA;
  }

  /**
   * Extracts project data directly from the raw JSON:API document's included section.
   */
  private void extractAndTrackProjects(JsonNode record, List<String> allColumns,
                                        List<Integer> projectColumnIndices,
                                        Map<String, Map<String, Object>> uniqueProjects) {
    Optional<JsonNode> relNodeOpt = JsonHelper.atJsonPtr(record, JSONApiDocumentStructure.RELATIONSHIP_PTR);
    Optional<JsonNode> includedNodeOpt = JsonHelper.atJsonPtr(record, JSONApiDocumentStructure.INCLUDED_PTR);

    if (relNodeOpt.isEmpty() || includedNodeOpt.isEmpty()) {
      return;
    }

    JsonNode projectsRel = relNodeOpt.get().get("projects");
    if (projectsRel == null || !projectsRel.has(JSONApiDocumentStructure.DATA)) {
      return;
    }

    JsonNode projectsData = projectsRel.get(JSONApiDocumentStructure.DATA);
    if (projectsData == null || projectsData.isNull()) {
      return;
    }

    List<Map<String, Object>> includedDoc = objectMapper.convertValue(includedNodeOpt.get(), LIST_MAP_TYPEREF);

    Iterator<JsonNode> projectRefs = projectsData.isArray() ? 
        projectsData.elements() : List.of(projectsData).iterator();

    while (projectRefs.hasNext()) {
      JsonNode projectRef = projectRefs.next();
      String projectId = projectRef.get(JSONApiDocumentStructure.ID).asText();

      if (uniqueProjects.containsKey(projectId)) {
        continue;
      }

      Map<String, Object> includedProject = extractById(projectId, includedDoc);
      if (includedProject.isEmpty()) {
        log.warn("Project {} not found in included section", projectId);
        continue;
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> projectAttributes = (Map<String, Object>) 
          includedProject.get(JSONApiDocumentStructure.ATTRIBUTES);
      if (projectAttributes == null) {
        continue;
      }

      Map<String, Object> projectData = new LinkedHashMap<>();
      projectData.put("id", projectId);

      Map<String, Object> flatProjectAttributes = 
          JSONApiDocumentStructure.mergeNestedMapUsingDotNotation(projectAttributes);

      for (Integer idx : projectColumnIndices) {
        String columnName = allColumns.get(idx);
        String attributeName = stripProjectPrefix(columnName);
        
        if (!"id".equals(attributeName)) {
          Object value = flatProjectAttributes.get(attributeName);
          if (value != null) {
            projectData.put(attributeName, value);
          }
        }
      }

      uniqueProjects.put(projectId, projectData);
    }
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

  // ========== JSON Processing Methods ==========

  private Map<String, Object> extractById(String id, List<Map<String, Object>> document) {
    DocumentContext dc = JsonPath.using(jsonPathConfiguration).parse(document);
    try {
      List<Map<String, Object>> includedObj = JsonPathHelper.extractById(dc, id, JSON_PATH_TYPE_REF);
      return CollectionUtils.isEmpty(includedObj) ? Map.of() : includedObj.getFirst();
    } catch (PathNotFoundException pnf) {
      return Map.of();
    }
  }

  /**
   * Takes relationships from the JSON:API document and extracts the nested documents (using the id)
   * from the nested section.
   *
   * @param jsonApiDocumentRecord
   * @return
   */
  private Map<String, Object> flatRelationships(JsonNode jsonApiDocumentRecord) {
    Optional<JsonNode> relNodeOpt = JsonHelper.atJsonPtr(jsonApiDocumentRecord, JSONApiDocumentStructure.RELATIONSHIP_PTR);
    Optional<JsonNode> includedNodeOpt = JsonHelper.atJsonPtr(jsonApiDocumentRecord, JSONApiDocumentStructure.INCLUDED_PTR);

    if (relNodeOpt.isEmpty() || includedNodeOpt.isEmpty()) {
      return Map.of();
    }

    Map<String, Object> flatRelationships = new HashMap<>();
    JsonNode relNode = relNodeOpt.get();
    List<Map<String, Object>> includedDoc = objectMapper.convertValue(includedNodeOpt.get(), LIST_MAP_TYPEREF);

    Iterator<String> relKeys = relNode.fieldNames();
    while (relKeys.hasNext()) {
      String relName = relKeys.next();
      JsonNode currRelNode = relNode.get(relName);
      
      if (!currRelNode.isArray() &&
          currRelNode.has(JSONApiDocumentStructure.DATA) &&
          !currRelNode.get(JSONApiDocumentStructure.DATA).isNull() &&
          !currRelNode.get(JSONApiDocumentStructure.DATA).isArray()) {
        String idValue = currRelNode.findValue(JSONApiDocumentStructure.ID).asText();
        flatRelationships.put(relName, extractById(idValue, includedDoc).get(JSONApiDocumentStructure.ATTRIBUTES));
      } else if (JsonHelper.hasFieldAndIsArray(currRelNode, JSONApiDocumentStructure.DATA)) {
        List<Map<String, Object>> toMerge = new ArrayList<>();
        currRelNode.get(JSONApiDocumentStructure.DATA).elements().forEachRemaining(el -> {
          String idValue = el.findValue(JSONApiDocumentStructure.ID).asText();
          Map<String, Object> includedDocMap = extractById(idValue, includedDoc);
          var doc = includedDocMap.get(JSONApiDocumentStructure.ATTRIBUTES);
          if (doc != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> docWithId = new HashMap<>((Map<String, Object>) doc);
            docWithId.put(JSONApiDocumentStructure.ID, includedDocMap.get(JSONApiDocumentStructure.ID));
            toMerge.add(docWithId);
          } else {
            log.warn("Can't find included document {}", idValue);
          }
        });
        flatRelationships.put(relName, flatToMany(toMerge));
      }
    }
    return flatRelationships;
  }

  /**
   * Gets all the text for the "attributes" specified by the columns (or constants) and concatenate
   * them using a separator.
   * @param attributeObjNod
   * @param function
   * @return
   */
  private static String handleConcatFunction(ObjectNode attributeObjNod, DataExportFunction function) {
    List<String> toConcat = new ArrayList<>();
    List<String> items = function.getParamAsList(DataExportFunction.CONCAT_PARAM_ITEMS);
    Map<String, String> constants = function.getParamAsMap(DataExportFunction.CONCAT_PARAM_CONSTANTS);
    String separator = function.params()
        .getOrDefault(DataExportFunction.CONCAT_PARAM_SEPARATOR, DataExportFunction.CONCAT_DEFAULT_SEP).toString();

    for (String col : items) {
      if (attributeObjNod.has(col)) {
        toConcat.add(JsonHelper.safeAsText(attributeObjNod, col));
      } else if (constants.containsKey(col)) {
        toConcat.add(constants.get(col));
      }
    }
    return String.join(separator, toConcat);
  }

  /**
   * Gets the coordinates from a geo_point column stored as [longitude,latitude] and return them as
   * decimal lat,long
   * @param attributeObjNod
   * @param function
   * @return
   */
  private static String handleConvertCoordinatesDecimalDegrees(ObjectNode attributeObjNod,
                                                               DataExportFunction function) {
    String column = function.getParamAsString(DataExportFunction.CONVERT_COORDINATES_DD_PARAM);
    JsonNode coordinates = attributeObjNod.get(column);
    
    if (coordinates != null && coordinates.isArray()) {
      List<JsonNode> longLatNode = IteratorUtils.toList(coordinates.iterator());
      if (longLatNode.size() == 2) {
        return String.format(DataExportFunction.COORDINATES_DD_FORMAT,
            longLatNode.get(1).asDouble(), longLatNode.get(0).asDouble());
      }
    }
    log.debug("Invalid Coordinates format. Array of doubles in form of [lon,lat] expected");
    return null;
  }

  /**
   * Creates a special document that represents all the values concatenated (by ; like the array elements) per attributes
   * @param toMerge
   * @return
   */
  public static Map<String, Object> flatToMany(List<Map<String, Object>> toMerge) {
    Map<String, Object> flatToManyRelationships = new HashMap<>();
    for (Map<String, Object> doc : toMerge) {
      if (doc == null) {
        continue;
      }
      for (var entry : doc.entrySet()) {
        // Skip null values - HashMap.merge throws NPE on null values
        if (entry.getValue() == null) {
          continue;
        }
        flatToManyRelationships.merge(entry.getKey(), entry.getValue(), 
            (v1, v2) -> v1 + ";" + v2);
      }
    }
    return flatToManyRelationships;
  }

  /**
   * Replaces nested map(s) with a key using dot notation.
   * Example: "managedAttributes": {"attribute_key" : "value"} becomes "managedAttributes.attribute_key" : "value"
   * @param attributeObjNode the object node where to replace the nested map(s)
   */
  private void replaceNestedByDotNotation(ObjectNode attributeObjNode) {
    JSONApiDocumentStructure.ExtractNestedMapResult nestedObjectsResult =
        JSONApiDocumentStructure.extractNestedMapUsingDotNotation(
            objectMapper.convertValue(attributeObjNode, MAP_TYPEREF));

    for (var nestedMap : nestedObjectsResult.nestedMapsMap().entrySet()) {
      attributeObjNode.set(nestedMap.getKey(), objectMapper.valueToTree(nestedMap.getValue()));
    }
    for (String key : nestedObjectsResult.usedKeys()) {
      attributeObjNode.remove(key);
    }
  }
}

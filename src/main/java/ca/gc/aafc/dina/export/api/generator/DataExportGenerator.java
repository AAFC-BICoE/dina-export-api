package ca.gc.aafc.dina.export.api.generator;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections.CollectionUtils;
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
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.output.CsvOutput;
import ca.gc.aafc.dina.export.api.output.DataOutput;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;
import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.jsonapi.JsonPathHelper;

import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.LIST_MAP_TYPEREF;
import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.MAP_TYPEREF;
import static ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure.atJsonPtr;

/**
 * Responsible to generate the export file.
 */
@Service
public class DataExportGenerator {
  public static final String DATA_EXPORT_CSV_FILENAME = "export.csv";

  private final DataExportStatusService dataExportStatusService;
  private final ObjectMapper objectMapper;
  private final ElasticSearchDataSource elasticSearchDataSource;
  private final Configuration jsonPathConfiguration;

  private final Path workingFolder;

  public DataExportGenerator(
    DataExportStatusService dataExportStatusService,
    DataExportConfig dataExportConfig,
    Configuration jsonPathConfiguration, ElasticSearchDataSource elasticSearchDataSource,
    ObjectMapper objectMapper) {

    this.dataExportStatusService = dataExportStatusService;
    this.jsonPathConfiguration = jsonPathConfiguration;
    this.elasticSearchDataSource = elasticSearchDataSource;
    this.objectMapper = objectMapper;

    workingFolder = dataExportConfig.getGeneratedDataExportsPath();
  }

  /**
   * main export method.
   * @param dinaExport
   * @return CompletableFuture that will
   */
  @Async(DataExportConfig.DINA_THREAD_POOL_BEAN_NAME)
  public CompletableFuture<UUID> export(DataExport dinaExport) throws IOException {

    try {
      System.out.println("Waiting from " + Thread.currentThread().getName());
      Thread.sleep(500);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    dataExportStatusService.updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.RUNNING);

    Path tmpDirectory = Files.createDirectories(workingFolder.resolve(dinaExport.getUuid().toString()));
    // csv output
    try (Writer w = new FileWriter(tmpDirectory.resolve(DATA_EXPORT_CSV_FILENAME).toFile(), StandardCharsets.UTF_8);
         CsvOutput<JsonNode> output =
           CsvOutput.create(Arrays.asList(dinaExport.getColumns()), new TypeReference<>() {
           }, w)) {
      export(dinaExport.getSource(), objectMapper.writeValueAsString(dinaExport.getQuery()), output);
    }

    dataExportStatusService.updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.DONE);

    return CompletableFuture.completedFuture(dinaExport.getUuid());
  }

  /**
   * Inner export method
   * @param sourceIndex
   * @param query
   * @param output
   * @throws IOException
   */
  private void export(String sourceIndex, String query, DataOutput<JsonNode> output) throws IOException {
    SearchResponse<JsonNode>
      response = elasticSearchDataSource.searchWithPIT(sourceIndex, query);

    boolean pageAvailable = response.hits().hits().size() != 0;

    while (pageAvailable) {
      for (Hit<JsonNode> hit : response.hits().hits()) {
        processRecord(hit.source(), output);
      }
      pageAvailable = false;

      int numberOfHits = response.hits().hits().size();
      // if we have a full page, try to get the next one
      if (ElasticSearchDataSource.ES_PAGE_SIZE == numberOfHits) {
        Hit<JsonNode> lastHit = response.hits().hits().get(numberOfHits - 1);
        response =
          elasticSearchDataSource.searchAfter(query, response.pitId(), lastHit.sort());
        pageAvailable = true;
      }
    }

    String pitId = response.pitId();
    elasticSearchDataSource.closePointInTime(pitId);
  }

  /**
   * @param record if null, the record will simply be skipped
   * @param output
   * @throws IOException
   */
  private void processRecord(JsonNode record, DataOutput<JsonNode> output) throws IOException {
    if (record == null) {
      return;
    }

    Optional<JsonNode> attributes = atJsonPtr(record, JSONApiDocumentStructure.ATTRIBUTES_PTR);
    if (attributes.isPresent() && attributes.get() instanceof ObjectNode attributeObjNode) {

      // handle nested maps (e.g. managed attributes)
      JSONApiDocumentStructure.ExtractNestedMapResult nestedObjectsResult =
        JSONApiDocumentStructure.extractNestedMapUsingDotNotation(objectMapper.convertValue(attributeObjNode, MAP_TYPEREF));

      // we add the nested objects with dot notation version
      for (var nestedMap : nestedObjectsResult.nestedMapsMap().entrySet()) {
        attributeObjNode.set(nestedMap.getKey(),
          objectMapper.valueToTree(nestedMap.getValue()));
      }
      // remove previous entries
      for (String key : nestedObjectsResult.usedKeys()) {
        attributeObjNode.remove(key);
      }

      // handle relationships
      Map<String, Object> flatRelationships = flatRelationships(record);
      Map<String, Object> flatRelationshipsDotNotation =
        JSONApiDocumentStructure.mergeNestedMapUsingDotNotation(flatRelationships);
      // we add the relationships in the attributes using dot notation
      for (var entry : flatRelationshipsDotNotation.entrySet()) {
        attributeObjNode.set(entry.getKey(),
          objectMapper.valueToTree(entry.getValue()));
      }
      output.addRecord(attributeObjNode);
    }
  }

  private Map<String, Object> extractById(String id, List<Map<String, Object>> document) {
    DocumentContext dc = JsonPath.using(jsonPathConfiguration).parse(document);
    TypeRef<List<Map<String, Object>>> typeRef = new TypeRef<>() {
    };
    try {
      List<Map<String, Object>> includedObj = JsonPathHelper.extractById(dc, id, typeRef);
      return CollectionUtils.isEmpty(includedObj) ? Map.of() : includedObj.get(0);
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

    Optional<JsonNode> relNodeOpt =
      atJsonPtr(jsonApiDocumentRecord, JSONApiDocumentStructure.RELATIONSHIP_PTR);
    Optional<JsonNode> includedNodeOpt =
      atJsonPtr(jsonApiDocumentRecord, JSONApiDocumentStructure.INCLUDED_PTR);

    if (relNodeOpt.isEmpty() || includedNodeOpt.isEmpty()) {
      return Map.of();
    }

    Map<String, Object> flatRelationships = new HashMap<>();
    JsonNode relNode = relNodeOpt.get();
    JsonNode includedNode = includedNodeOpt.get();

    List<Map<String, Object>> includedDoc = objectMapper.convertValue(includedNode, LIST_MAP_TYPEREF);

    // loop over relationships
    Iterator<String> relKeys = relNode.fieldNames();
    while (relKeys.hasNext()) {
      String relName = relKeys.next();
      JsonNode currRelNode = relNode.get(relName);
      // if it's not an array (to-one), we can just take it as is
      if (!currRelNode.isArray() &&
        currRelNode.has(JSONApiDocumentStructure.DATA) &&
        !currRelNode.get(JSONApiDocumentStructure.DATA).isNull() &&
        !currRelNode.get(JSONApiDocumentStructure.DATA).isArray()) {
        // get the id value from the relationships section
        String idValue = currRelNode.findValue(JSONApiDocumentStructure.ID).asText();
        // pull the nested-document from the included section
        flatRelationships.put(relName,
          extractById(idValue, includedDoc).get(JSONApiDocumentStructure.ATTRIBUTES));
      }
    }

    return flatRelationships;
  }

  /**
   * Result of the Export request.
   * @param resultIdentifier
   */
  public record ExportResult(UUID resultIdentifier) {

  }

}

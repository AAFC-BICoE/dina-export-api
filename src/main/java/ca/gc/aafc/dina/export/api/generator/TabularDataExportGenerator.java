package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
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

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.log4j.Log4j2;

/**
 * Responsible to generate tabular export file.
 */
@Service
@Log4j2
public class TabularDataExportGenerator extends DataExportGenerator {

  private static final TypeRef<List<Map<String, Object>>> JSON_PATH_TYPE_REF = new TypeRef<>() {
  };

  private final ObjectMapper objectMapper;
  private final ElasticSearchDataSource elasticSearchDataSource;
  private final Configuration jsonPathConfiguration;

  private final DataExportConfig dataExportConfig;

  public TabularDataExportGenerator(
    DataExportStatusService dataExportStatusService,
    DataExportConfig dataExportConfig,
    Configuration jsonPathConfiguration, ElasticSearchDataSource elasticSearchDataSource,
    ObjectMapper objectMapper) {

    super(dataExportStatusService);

    this.jsonPathConfiguration = jsonPathConfiguration;
    this.elasticSearchDataSource = elasticSearchDataSource;
    this.objectMapper = objectMapper;
    this.dataExportConfig = dataExportConfig;
  }

  /**
   * main export method.
   * @param dinaExport
   * @return CompletableFuture that will
   */
  @Async(DataExportConfig.DINA_THREAD_POOL_BEAN_NAME)
  public CompletableFuture<UUID> export(DataExport dinaExport) throws IOException {

    DataExport.ExportStatus currStatus = waitForRecord(dinaExport.getUuid());

    // Should only work for NEW record at this point
    if (DataExport.ExportStatus.NEW == currStatus) {

      Path exportPath = dataExportConfig.getPathForDataExport(dinaExport);
      if (exportPath == null) {
        log.error("Null export path");
        updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
        return CompletableFuture.completedFuture(dinaExport.getUuid());
      }

      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.RUNNING);

      try {
        //Create the directory
        ensureDirectoryExists(exportPath.getParent());

        List<String> headerAliases = dinaExport.getColumnAliases() != null ?
          Arrays.asList(dinaExport.getColumnAliases()) : null;

        // csv output
        try (Writer w = new FileWriter(exportPath.toFile(), StandardCharsets.UTF_8);
             CsvOutput<JsonNode> output =
               CsvOutput.create(Arrays.asList(dinaExport.getColumns()), headerAliases,
                 new TypeReference<>() {
                 }, w)) {
          export(dinaExport.getSource(), objectMapper.writeValueAsString(dinaExport.getQuery()),
            dinaExport.getColumnFunctions(), output);
        }
      } catch (IOException ioEx) {
        updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
        throw ioEx;
      }
      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.COMPLETED);
    } else {
      log.error("Unexpected DataExport status: {}", currStatus);
    }

    return CompletableFuture.completedFuture(dinaExport.getUuid());
  }

  @Override
  public void deleteExport(DataExport dinaExport) throws IOException {

    if (dinaExport.getExportType() != DataExport.ExportType.TABULAR_DATA) {
      throw new IllegalArgumentException("Should only be used for ExportType TABULAR_DATA");
    }

    Path exportPath = dataExportConfig.getPathForDataExport(dinaExport);
    deleteIfExists(exportPath);

    if (DataExportConfig.isExportTypeUsesDirectory(DataExport.ExportType.TABULAR_DATA) &&
      DataExportConfig.isDataExportDirectory(exportPath.getParent(), dinaExport)) {
      deleteIfExists(exportPath.getParent());
    }
  }

  /**
   * Inner export method
   * @param sourceIndex
   * @param query
   * @param output
   * @throws IOException
   */
  private void export(String sourceIndex, String query,
                      Map<String, DataExport.FunctionDef> columnFunctions,
                      DataOutput<JsonNode> output) throws IOException {
    SearchResponse<JsonNode>
      response = elasticSearchDataSource.searchWithPIT(sourceIndex, query);

    boolean pageAvailable = response.hits().hits().size() != 0;
    while (pageAvailable) {
      for (Hit<JsonNode> hit : response.hits().hits()) {
        processRecord(hit.id(), hit.source(), columnFunctions, output);
      }
      pageAvailable = false;

      int numberOfHits = response.hits().hits().size();
      // if we have a full page, try to get the next one
      if (elasticSearchDataSource.getPageSize() == numberOfHits) {
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
  private void processRecord(String documentId, JsonNode record,
                             Map<String, DataExport.FunctionDef> columnFunctions,
                             DataOutput<JsonNode> output) throws IOException {
    if (record == null) {
      return;
    }

    Optional<JsonNode> attributes = atJsonPtr(record, JSONApiDocumentStructure.ATTRIBUTES_PTR);
    if (attributes.isPresent() && attributes.get() instanceof ObjectNode attributeObjNode) {

      attributeObjNode.put(JSONApiDocumentStructure.ID, documentId);

      // handle nested maps (e.g. managed attributes)
      replaceNestedByDotNotation(attributeObjNode);

      // handle relationships
      // Get a map of all relationships (to-one only)
      Map<String, Object> flatRelationships = flatRelationships(record);

      // transform "collectingEvent": {"location" : "value"} in "collectingEvent.location" : "value"
      Map<String, Object> flatRelationshipsDotNotation =
        JSONApiDocumentStructure.mergeNestedMapUsingDotNotation(flatRelationships);
      // we add the relationships in the attributes using dot notation
      for (var entry : flatRelationshipsDotNotation.entrySet()) {
        attributeObjNode.set(entry.getKey(),
          objectMapper.valueToTree(entry.getValue()));
        replaceNestedByDotNotation(attributeObjNode);
      }

      // Check if we have functions to apply
      if(MapUtils.isNotEmpty(columnFunctions)) {
        for (var functionDef : columnFunctions.entrySet()) {
          switch (functionDef.getValue().type()) {
            case CONCAT -> attributeObjNode.put(functionDef.getKey(),
              handleConcatFunction(attributeObjNode, functionDef.getValue().params()));
            case CONVERT_COORDINATES_DD -> attributeObjNode.put(functionDef.getKey(),
              handleConvertCoordinatesDecimalDegrees(attributeObjNode,
                functionDef.getValue().params()));
          }
        }
      }
      output.addRecord(attributeObjNode);
    }
  }

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
      } else if (jsonNodeHasFieldAndIsArray(currRelNode, JSONApiDocumentStructure.DATA)) {
        // if "data" is an array (to-many)
        List<Map<String, Object>> toMerge = new ArrayList<>();
        currRelNode.get(JSONApiDocumentStructure.DATA).elements().forEachRemaining(el -> {
          String idValue = el.findValue(JSONApiDocumentStructure.ID).asText();
          // pull the included-document from the included section
          var doc = extractById(idValue, includedDoc).get(JSONApiDocumentStructure.ATTRIBUTES);
          if (doc != null) {
            toMerge.add((Map<String, Object>) doc);
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
   * Checks if a JSON node has a specific field and if that field's value is an array.
   *
   * @param node The JSON node to check.
   * @param fieldName The name of the field to check.
   * @return True if the node has the field and its value is an array, false otherwise.
   */
  private static boolean jsonNodeHasFieldAndIsArray(JsonNode node, String fieldName) {
    return node.has(fieldName) && node.get(fieldName).isArray();
  }

  private static String handleConcatFunction(ObjectNode attributeObjNod, List<String> columns) {
    List<String> toConcat = new ArrayList<>();
    for(String col : columns) {
      toConcat.add(attributeObjNod.get(col).asText());
    }
    return String.join(",", toConcat);
  }

  private static String handleConvertCoordinatesDecimalDegrees(ObjectNode attributeObjNod,
                                                               List<String> columns) {
    String decimalDegreeCoordinates = null;
    if (columns.size() == 1) {
      JsonNode coordinates = attributeObjNod.get(columns.getFirst());
      if (coordinates.isArray()) {
        List<JsonNode> longLatNode = IteratorUtils.toList(coordinates.iterator());
        if (longLatNode.size() == 2) {
          decimalDegreeCoordinates =
            longLatNode.get(1).asText() + "," + longLatNode.get(0).asText();
        }
      }
    }
    if (StringUtils.isBlank(decimalDegreeCoordinates)) {
      log.debug("Invalid Coordinates format. Array of doubles in form of [lon,lat] expected");
    }
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
      for (var entry : doc.entrySet()) {
        if (flatToManyRelationships.containsKey(entry.getKey())) {
          flatToManyRelationships.computeIfPresent(entry.getKey(),
            (k, v) -> v + ";" + entry.getValue());
        } else {
          flatToManyRelationships.put(entry.getKey(), entry.getValue());
        }
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
  }

}

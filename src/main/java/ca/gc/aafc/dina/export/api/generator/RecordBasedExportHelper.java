package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.TypeRef;

import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;
import ca.gc.aafc.dina.json.JsonHelper;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.jsonapi.JsonPathHelper;

import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.LIST_MAP_TYPEREF;
import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.MAP_TYPEREF;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;

/**
 * Helper class containing shared functionality for record-based export generators.
 * This includes JSON processing, relationship flattening, and function applications.
 */
@Component
@Log4j2
public class RecordBasedExportHelper {

  private static final TypeRef<List<Map<String, Object>>> JSON_PATH_TYPE_REF = new TypeRef<>() { };

  private final ObjectMapper objectMapper;
  private final ElasticSearchDataSource elasticSearchDataSource;
  private final Configuration jsonPathConfiguration;

  public RecordBasedExportHelper(
      Configuration jsonPathConfiguration,
      ElasticSearchDataSource elasticSearchDataSource,
      ObjectMapper objectMapper) {
    this.jsonPathConfiguration = jsonPathConfiguration;
    this.elasticSearchDataSource = elasticSearchDataSource;
    this.objectMapper = objectMapper;
  }

  /**
   * Prepares an attribute node by flattening nested maps and relationships.
   */
  public ObjectNode prepareAttributeNode(String documentId, JsonNode record) {
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
  public void applyFunctions(ObjectNode attributeObjNode, Map<String, DataExportFunction> columnFunctions) {
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
   * Flattens record relationships.
   */
  public void flattenRecordRelationships(ObjectNode attributeObjNode, JsonNode record) {
    replaceNestedByDotNotation(attributeObjNode);
    Map<String, Object> flatRelationships = flatRelationships(record);
    Map<String, Object> flatRelationshipsDotNotation =
        JSONApiDocumentStructure.mergeNestedMapUsingDotNotation(flatRelationships);
    for (var entry : flatRelationshipsDotNotation.entrySet()) {
      attributeObjNode.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
      replaceNestedByDotNotation(attributeObjNode);
    }
  }

  // ========== JSON Processing Methods ==========

  public Map<String, Object> extractById(String id, List<Map<String, Object>> document) {
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
  public Map<String, Object> flatRelationships(JsonNode jsonApiDocumentRecord) {
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

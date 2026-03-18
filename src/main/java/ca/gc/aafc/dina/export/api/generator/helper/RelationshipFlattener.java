package ca.gc.aafc.dina.export.api.generator.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections.CollectionUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.TypeRef;

import ca.gc.aafc.dina.json.JsonHelper;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.jsonapi.JsonPathHelper;

import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.LIST_MAP_TYPEREF;

import lombok.extern.log4j.Log4j2;

/**
 * Extracts and flattens JSON:API relationships from Elasticsearch documents,
 * resolving relationship references against "included" section.
 */
@Log4j2
public class RelationshipFlattener {

  private static final TypeRef<List<Map<String, Object>>> JSON_PATH_TYPE_REF = new TypeRef<>() {
  };
  private static final String TO_MANY_SEPARATOR = ";";

  private final ObjectMapper objectMapper;
  private final Configuration jsonPathConfiguration;

  public RelationshipFlattener(ObjectMapper objectMapper, Configuration jsonPathConfiguration) {
    this.objectMapper = objectMapper;
    this.jsonPathConfiguration = jsonPathConfiguration;
  }

  /**
   * Merges flattened relationships from record into the attributes node,
   * applying dot-notation for nested values.
   *
   * @param record the full record containing /data/relationships and /included
   * @param attributes the attributes node to merge relationship data into
   */
  public void mergeRelationshipsIntoAttributes(JsonNode record, ObjectNode attributes) {
    Map<String, Object> flatRelationships = flatRelationships(record);
    Map<String, Object> dotNotationRelationships =
      JSONApiDocumentStructure.mergeNestedMapUsingDotNotation(flatRelationships);

    for (var entry : dotNotationRelationships.entrySet()) {
      attributes.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
    }
  }

  /**
   * Extracts and flattens all relationships from a document, resolving
   * relationship references by looking them up in the "included" section.
   *
   * @param record document
   * @return map of relationship name to resolved attributes
   */
  Map<String, Object> flatRelationships(JsonNode record) {
    Optional<JsonNode> relationshipsOpt = JsonHelper.atJsonPtr(record, JSONApiDocumentStructure.RELATIONSHIP_PTR);
    Optional<JsonNode> includedOpt = JsonHelper.atJsonPtr(record, JSONApiDocumentStructure.INCLUDED_PTR);

    if (relationshipsOpt.isEmpty() || includedOpt.isEmpty()) {
      return Map.of();
    }

    JsonNode relationships = relationshipsOpt.get();
    List<Map<String, Object>> includedDocs =
      objectMapper.convertValue(includedOpt.get(), LIST_MAP_TYPEREF);

    Map<String, Object> result = new HashMap<>();
    Iterator<String> names = relationships.fieldNames();

    while (names.hasNext()) {
      String name = names.next();
      JsonNode relData = relationships.get(name);
      processRelationship(name, relData, includedDocs, result);
    }
    return result;
  }

  private void processRelationship(String name, JsonNode relData,
                                   List<Map<String, Object>> includedDocs,
                                   Map<String, Object> result) {
    if (isToOneRelationship(relData)) {
      String id = relData.findValue(JSONApiDocumentStructure.ID).asText();
      result.put(name, extractAttributesById(id, includedDocs));
    } else if (JsonHelper.hasFieldAndIsArray(relData, JSONApiDocumentStructure.DATA)) {
      result.put(name, resolveToManyRelationship(relData, includedDocs));
    }
  }

  private static boolean isToOneRelationship(JsonNode relData) {
    return !relData.isArray()
      && relData.has(JSONApiDocumentStructure.DATA)
      && !relData.get(JSONApiDocumentStructure.DATA).isNull()
      && !relData.get(JSONApiDocumentStructure.DATA).isArray();
  }

  private Object extractAttributesById(String id, List<Map<String, Object>> includedDocs) {
    DocumentContext dc = JsonPath.using(jsonPathConfiguration).parse(includedDocs);
    try {
      List<Map<String, Object>> matches = JsonPathHelper.extractById(dc, id, JSON_PATH_TYPE_REF);
      Map<String, Object> doc = CollectionUtils.isEmpty(matches) ? Map.of() : matches.getFirst();
      return doc.get(JSONApiDocumentStructure.ATTRIBUTES);
    } catch (PathNotFoundException pnf) {
      return null;
    }
  }

  private Map<String, Object> resolveToManyRelationship(JsonNode relData,
                                                         List<Map<String, Object>> includedDocs) {
    List<Map<String, Object>> relatedAttributes = new ArrayList<>();

    relData.get(JSONApiDocumentStructure.DATA).elements().forEachRemaining(element -> {
      String id = element.findValue(JSONApiDocumentStructure.ID).asText();
      Object attributes = extractAttributesById(id, includedDocs);

      if (attributes instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> attrMap = (Map<String, Object>) attributes;
        relatedAttributes.add(attrMap);
      } else {
        log.warn("Cannot find included document with ID: {}", id);
      }
    });

    return flatToMany(relatedAttributes);
  }

  /**
   * Flattens to-many relationships by concatenating attribute values with semicolons.
   * Example: [{name: "A"}, {name: "B"}] becomes {name: "A;B"}
   *
   * @param documents list of attribute maps to merge
   * @return merged map with concatenated values
   */
  private static Map<String, Object> flatToMany(List<Map<String, Object>> documents) {
    if (documents == null || documents.isEmpty()) {
      return new HashMap<>();
    }
    
    Map<String, Object> merged = new HashMap<>();
    
    for (Map<String, Object> doc : documents) {
      if (doc == null) {
        continue;
      }
      
      for (var entry : doc.entrySet()) {
        Object value = entry.getValue();
        
        // Skip null values entirely - they have no data to export
        if (value == null) {
          continue;
        }
        
        merged.merge(entry.getKey(), value,
          (existing, newValue) -> String.valueOf(existing) + TO_MANY_SEPARATOR + String.valueOf(newValue));
      }
    }
    
    return merged;
  }
}

package ca.gc.aafc.dina.export.api.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import okhttp3.HttpUrl;

/**
 * Resolves relationships to resources that are not part of the Elasticsearch document
 * (e.g. collectingEvent.collectors -> person) by fetching them from the DINA API owning them.
 * 
 * External resources are defined as environment variables specifying the API URL and resource types.
 * Any new api that needs to be resolved externally should be added as environment variables 
 * specifying its URL and the resource types it serves.
 */
@Service
public class ExternalRelationshipResolver {

  private final DinaApiClient dinaApiClient;

  // Map containing JSON:API type and the url of the DINA API serving it.
  private final Map<String, String> apiUrlByType = new HashMap<>();

  /**
   * Constructs an ExternalRelationshipResolver with the given DINA API client and environment 
   * variable values for external APIs.
   * 
   * When adding new external APIs, provide their URL and resource types as environment variables 
   * and include them in the constructor and register them using the registerTypes method.
   */
  public ExternalRelationshipResolver(DinaApiClient dinaApiClient,
                                      @Value("${dina.export.agent.apiUrl}") String agentApiUrl,
                                      @Value("${dina.export.agent.resourceTypes}") String agentResourceTypes,
                                      @Value("${dina.export.objectStore.apiUrl}") String objectStoreApiUrl,
                                      @Value("${dina.export.objectStore.resourceTypes}") String objectStoreResourceTypes) {
    this.dinaApiClient = dinaApiClient;
    registerTypes(agentResourceTypes, agentApiUrl);
    registerTypes(objectStoreResourceTypes, objectStoreApiUrl);
  }

  /**
   * Registers the given API URL for each of the specified JSON:API types.
   * The types should be provided as a comma-separated string.
   * 
   * @param commaSeparatedTypes comma-separated list of JSON:API types served by the API URL
   * @param apiUrl the URL of the DINA API serving the specified types
   */
  private void registerTypes(String commaSeparatedTypes, String apiUrl) {
    for (String type : StringUtils.tokenizeToStringArray(commaSeparatedTypes, ",")) {
      apiUrlByType.put(type, apiUrl);
    }
  }

  /**
   * Resolves a single resource by its JSON:API type from the DINA API serving that type.
   *
   * @param type JSON:API type (e.g. person)
   * @param uuid uuid of the resource
   * @param include optional relationships to include (e.g. organizations)
   * @return the resource document, or null if the type is not served by a configured api or the resource is not found
   */
  public JsonApiDocument resolve(String type, UUID uuid, String... include) {
    // only configured types are fetched, the type comes from the indexed document
    String apiUrl = type == null ? null : apiUrlByType.get(type);
    if (apiUrl == null || uuid == null) {
      return null;
    }
    return fetchDocument(apiUrl, type, uuid, include);
  }

  /**
   * Resolves the relationships of a resource that point to externally resolvable types.
   *
   * @param parentPath name of the resource in the export (e.g. collectingEvent)
   * @param relationships the "relationships" node of the resource
   * @param requestedPaths relationship paths to resolve (e.g. collectingEvent.collectors), others are skipped
   * @return relationship name -> attributes (including id) of each resolved resource
   */
  public Map<String, List<Map<String, Object>>> resolveRelationships(String parentPath, JsonNode relationships,
                                                                     Set<String> requestedPaths) {
    Map<String, List<Map<String, Object>>> resolved = new LinkedHashMap<>();
    if (relationships == null || !relationships.isObject()
        || requestedPaths == null || requestedPaths.isEmpty()) {
      return resolved;
    }

    relationships.fields().forEachRemaining(relationship -> {
      String name = relationship.getKey();

      // Only resolve relationships the caller asked to be included
      if (!requestedPaths.contains(parentPath + "." + name)) {
        return;
      }

      List<Map<String, Object>> documents = resolveReferences(relationship.getValue());
      if (!documents.isEmpty()) {
        resolved.put(name, documents);
      }
    });
    return resolved;
  }

  // Resolves every resource referenced by a relationship, skipping any that can't be fetched
  private List<Map<String, Object>> resolveReferences(JsonNode relationship) {
    JsonNode data = relationship.path(JSONApiDocumentStructure.DATA);

    // To-one relationships hold a single reference, to-many relationships hold an array
    Iterable<JsonNode> refs = data.isArray() ? data : List.of(data);

    List<Map<String, Object>> documents = new ArrayList<>();
    for (JsonNode ref : refs) {
      Map<String, Object> attributes = resolveReference(ref);
      if (attributes != null) {
        documents.add(attributes);
      }
    }
    return documents;
  }

  // Returns the referenced resource's attributes plus its id, or null if it can't be resolved
  private Map<String, Object> resolveReference(JsonNode ref) {
    String type = ref.path(JSONApiDocumentStructure.TYPE).asText();
    UUID uuid = parseUuid(ref.path(JSONApiDocumentStructure.ID).asText());

    JsonApiDocument document = resolve(type, uuid);
    if (document == null || document.getAttributes() == null) {
      return null;
    }

    // Copy so the fetched document isn't modified
    Map<String, Object> attributes = new LinkedHashMap<>(document.getAttributes());
    attributes.put(JSONApiDocumentStructure.ID, uuid.toString());
    return attributes;
  }

  private JsonApiDocument fetchDocument(String apiUrl, String type, UUID uuid, String[] include) {
    HttpUrl baseUrl = HttpUrl.parse(apiUrl);
    if (baseUrl == null) {
      return null;
    }

    HttpUrl.Builder url = baseUrl.newBuilder().addPathSegment(type).addPathSegment(uuid.toString());
    if (include.length > 0) {
      url.addQueryParameter("include", String.join(",", include));
    }
    return dinaApiClient.fetchDocument(url.build());
  }

  // ids come from the indexed document, only accept uuids before building a url with them
  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}

package ca.gc.aafc.dina.export.api.generator.helper;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.service.DinaApiClient;
import ca.gc.aafc.dina.json.JsonHelper;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;

import java.util.Map;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;

/**
 * Maps DINA resource to DarwinCore terms
 *
 * For each column mapping, extracts the value from the resource context.
 *
 * Handles:
 * - Simple field extraction via dot notation
 * - Array filtering (e.g., isPrimary == true)
 * - Static values
 * - Null safety and error handling
 */
@Component
@Log4j2
public class DarwinCoreMapper {

  private static final String FILTER_KEY_PARAM = "filter[key]";
  private static final String DINA_COMPONENT_PARAM = "dinaComponent";

  private final DinaApiClient dinaApiClient;
  private final DataExportConfig dataExportConfig;

  public DarwinCoreMapper(DinaApiClient dinaApiClient, DataExportConfig dataExportConfig) {
    this.dinaApiClient = dinaApiClient;
    this.dataExportConfig = dataExportConfig;
  }

  /**
   * Extract a DarwinCore term value from the entities context
   *
   * Processing order (first match wins):
   * 1. Static value (if defined)
   * 2. Array filtering (if filter is defined)
   * 3. Simple path navigation
   *
   * @param entitiesContext Map of entity contexts built by DarwinCoreContextBuilder
   * @param mapping The column mapping configuration from YAML
   * @return The extracted value, or null if not found
   *
   * Example:
   * mapping = {
   *   dwcTerm: "kingdom",
   *   context: "determination",
   *   source: "kingdom"
   * }
   * Result: Gets the kingdom value from the determination context
   */
  public String extractValue(Map<String, JsonNode> entitiesContext, DarwinCoreExportConfig.ColumnMapping mapping) {
    // 1. Static value (highest priority)
    if (mapping.getStaticValue() != null) {
      log.debug("Using static value for {}: {}", mapping.getDwcTerm(), mapping.getStaticValue());
      return mapping.getStaticValue();
    }

    // 2. Get context node from entities map
    JsonNode contextNode = entitiesContext.get(mapping.getContext());
    if (contextNode == null) {
      log.debug("Context not found: {} for DwC term: {}", mapping.getContext(), mapping.getDwcTerm());
      return null;
    }

    // 3. Filter + optional subpath (JSONPath filter syntax, e.g. @.placeType == 'county')
    if (mapping.getFilter() != null) {

      //Sanity check. Make sure the source exists.
      JsonNode checkNode = contextNode.at("/" + mapping.getSource());
      if (checkNode.isMissingNode() || checkNode.isNull()) {
        return null;
      }

      String expression = "$." + mapping.getSource() + "[?(" + mapping.getFilter() + ")]"
          + (mapping.getPath() != null ? "." + mapping.getPath() : "");
      JsonNode result = JsonHelper.findOneInJsonNode(contextNode, expression);
      return result != null && !result.isNull() ? result.asText() : null;
    }

    if (mapping.getVocabularyValue() != null) {
      return resolveApiReferencedValues(contextNode, mapping);
    }

    // Collect the value
    // from each element and join them with the configured separator.
    if (contextNode.isArray()) {
      return joinArrayValues((ArrayNode) contextNode, mapping.getSource(), mapping.getSeparator());
    }
    JsonNode value = JsonHelper.findOneInJsonNode(contextNode, "$." + mapping.getSource());
    return value != null && !value.isNull() ? value.asText() : null;
  }

  /**
   * Extracts a value from each element of a to-many context and joins them.
   *
   * @param array to-many context array
   * @param source dot-notation path relative to each array element
   * @param separator separator to use between values
   * @return joined text or null if no non-null value was found
   */
  private static String joinArrayValues(ArrayNode array, String source, String separator) {
    StringBuilder sb = new StringBuilder();
    for (JsonNode element : array) {
      JsonNode value = JsonHelper.findOneInJsonNode(element, "$." + source);
      if (value == null || value.isNull()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(separator);
      }
      sb.append(value.asText());
    }
    return sb.length() > 0 ? sb.toString() : null;
  }

  /**
   * Resolves values referenced by an external DINA API (e.g. a controlled-vocabulary-item).
   *
   * Each element of a to-many context (e.g. an attachment array) holds a map of managed
   * attribute keys to values (the mapping's {@code source}). For each key the matching
   * document is fetched from the API (and cached) and the configured {@code vocabularyValue}
   * field (e.g. uriTemplate) is extracted. The {@code valuePlaceholder} in that field is
   * replaced with the actual managed attribute value, and all resolved values are joined
   * with the separator.
   *
   * @param contextNode the to-many context node (array)
   * @param mapping the column mapping
   * @return the joined resolved values, or null if none could be resolved
   */
  private String resolveApiReferencedValues(JsonNode contextNode, DarwinCoreExportConfig.ColumnMapping mapping) {
    if (!contextNode.isArray()) {
      log.warn("API referenced value resolution for {} requires an array context, but {} is not an array",
        mapping.getDwcTerm(), mapping.getContext());
      return null;
    }

    StringBuilder sb = new StringBuilder();
    for (JsonNode element : contextNode) {
      JsonNode managedAttributes = JsonHelper.findOneInJsonNode(element, "$." + mapping.getSource());
      if (managedAttributes == null || !managedAttributes.isObject()) {
        continue;
      }

      managedAttributes.properties().forEach(entry -> {
        if (mapping.getVocabularyKey() != null && !mapping.getVocabularyKey().equals(entry.getKey())) {
          return;
        }

        if (entry.getValue() == null || entry.getValue().isNull()) {
          return;
        }

        JsonApiDocument doc = dinaApiClient.fetchDocument(
          buildReferencedValueUrl(mapping.getVocabularyDocumentType(), mapping.getDinaComponent(), entry.getKey()));
        if (doc == null || doc.getAttributes() == null) {
          log.warn("No referenced document found for key '{}' (dwcTerm={})",
            entry.getKey(), mapping.getDwcTerm());
          return;
        }

        Object referencedValue = doc.getAttributes().get(mapping.getVocabularyValue());
        if (referencedValue == null) {
          log.warn("Referenced document for key '{}' has no '{}' attribute (dwcTerm={})",
            entry.getKey(), mapping.getVocabularyValue(), mapping.getDwcTerm());
          return;
        }

        String resolved = String.valueOf(referencedValue)
          .replace(mapping.getValuePlaceholder(), entry.getValue().asText());

        if (sb.length() > 0) {
          sb.append(mapping.getSeparator());
        }
        sb.append(resolved);
      });
    }

    if (sb.length() == 0) {
      return null;
    }
    return sb.toString();
  }

  /**
   * Builds the URL to fetch a referenced document from the object store API.
   *
   * @param type the JSON:API resource type (e.g. "controlled-vocabulary-item")
   * @param dinaComponent the dinaComponent filter value (e.g. "METADATA"), or null/blank to omit it
   * @param key the value to filter on, typically a managed attribute key
   * @return the URL, or null if the resource type is not configured, or the object store API URL
   *         is not configured or invalid
   */
  private HttpUrl buildReferencedValueUrl(String type, String dinaComponent, String key) {
    if (type == null || type.isBlank()) {
      log.warn("No vocabularyDocumentType configured for column resolving key {}", key);
      return null;
    }

    String baseUrl = dataExportConfig.getObjectStoreApiUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      log.warn("objectStoreApiUrl is not configured, unable to resolve {} for key {}", type, key);
      return null;
    }

    HttpUrl url = HttpUrl.parse(baseUrl);
    if (url == null) {
      log.warn("Invalid objectStoreApiUrl: {}", baseUrl);
      return null;
    }

    HttpUrl.Builder urlBuilder = url.newBuilder()
      .addPathSegment(type)
      .addQueryParameter(FILTER_KEY_PARAM, key);
    if (dinaComponent != null && !dinaComponent.isBlank()) {
      urlBuilder.addQueryParameter(DINA_COMPONENT_PARAM, dinaComponent);
    }
    return urlBuilder.build();
  }

}

package ca.gc.aafc.dina.export.api.generator.helper;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
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

  public DarwinCoreMapper(DinaApiClient dinaApiClient) {
    this.dinaApiClient = dinaApiClient;
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

        HttpUrl url = buildReferencedValueUrl(mapping, entry.getKey());
        if (url == null) {
          return;
        }
        JsonApiDocument doc = dinaApiClient.fetchDocument(url);
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
   * Builds the URL to fetch a referenced document.
   *
   * The base URL comes from the column mapping's {@code vocabularyUrlTemplate}, e.g.
   * "${dina.export.objectStoreApiUrl}/controlled-vocabulary-item". The {@code filter[key]} query
   * parameter is added with the managed attribute key, and the optional {@code dinaComponent}
   * filter value from the mapping is added when configured.
   *
   * @param mapping the column mapping that defines the referenced document URL template
   * @param key the value to filter on, typically a managed attribute key
   * @return the URL, or null if no URL template is configured or it is invalid
   */
  private HttpUrl buildReferencedValueUrl(DarwinCoreExportConfig.ColumnMapping mapping, String key) {
    String urlTemplate = mapping.getVocabularyUrlTemplate();
    if (urlTemplate == null || urlTemplate.isBlank()) {
      log.warn("No vocabularyUrlTemplate configured for column resolving key {}", key);
      return null;
    }

    HttpUrl url = HttpUrl.parse(urlTemplate);
    if (url == null) {
      log.warn("Invalid vocabularyUrlTemplate: {}", urlTemplate);
      return null;
    }

    HttpUrl.Builder urlBuilder = url.newBuilder()
      .addQueryParameter(FILTER_KEY_PARAM, key);
    if (mapping.getDinaComponent() != null && !mapping.getDinaComponent().isBlank()) {
      urlBuilder.addQueryParameter(DINA_COMPONENT_PARAM, mapping.getDinaComponent());
    }
    return urlBuilder.build();
  }

}

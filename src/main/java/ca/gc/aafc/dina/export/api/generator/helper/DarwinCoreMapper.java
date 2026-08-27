package ca.gc.aafc.dina.export.api.generator.helper;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
import ca.gc.aafc.dina.export.api.service.ObjectStoreApiClient;
import ca.gc.aafc.dina.json.JsonHelper;

import java.util.Map;
import lombok.extern.log4j.Log4j2;

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

  private final ObjectStoreApiClient objectStoreApiClient;

  public DarwinCoreMapper(ObjectStoreApiClient objectStoreApiClient) {
    this.objectStoreApiClient = objectStoreApiClient;
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
      return resolveVocabularyValues(contextNode, mapping);
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
   * Resolves managed attribute values through the controlled-vocabulary-item.
   *
   * Each element of a to-many context (e.g. an attachment array) holds a map of managed
   * attribute keys to values (the mapping's {@code source}). For each key the matching
   * controlled-vocabulary-item is fetched (and cached) and the configured
   * {@code vocabularyValue} field (e.g. uriTemplate) is extracted. The
   * {@code valuePlaceholder} in that field is replaced with the actual managed attribute
   * value, and all resolved values are joined with the separator.
   *
   * @param contextNode the to-many context node (array)
   * @param mapping the column mapping
   * @return the joined resolved values, or null if none could be resolved
   */
  private String resolveVocabularyValues(JsonNode contextNode, DarwinCoreExportConfig.ColumnMapping mapping) {
    if (!contextNode.isArray()) {
      log.warn("Vocabulary resolution for {} requires an array context, but {} is not an array",
        mapping.getDwcTerm(), mapping.getContext());
      return null;
    }

    StringBuilder sb = new StringBuilder();
    for (JsonNode element : contextNode) {
      JsonNode managedAttributes = JsonHelper.findOneInJsonNode(element, "$." + mapping.getSource());
      if (managedAttributes == null || !managedAttributes.isObject()) {
        continue;
      }

      if (managedAttributes.isEmpty()) {
      }

      managedAttributes.properties().forEach(entry -> {
        if (mapping.getVocabularyKey() != null && !mapping.getVocabularyKey().equals(entry.getKey())) {
          return;
        }

        if (entry.getValue() == null || entry.getValue().isNull()) {
          return;
        }

        JsonNode item = objectStoreApiClient.getControlledVocabularyItem(entry.getKey());
        if (item == null) {
          log.warn("No controlled-vocabulary-item found for managed attribute key '{}' (dwcTerm={})",
            entry.getKey(), mapping.getDwcTerm());
          return;
        }

        JsonNode vocabValue = item.at("/attributes/" + mapping.getVocabularyValue());
        if (vocabValue.isMissingNode() || vocabValue.isNull()) {
          log.warn("controlled-vocabulary-item for key '{}' has no '{}' attribute (dwcTerm={})",
            entry.getKey(), mapping.getVocabularyValue(), mapping.getDwcTerm());
          return;
        }

        String resolved = vocabValue.asText()
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

}

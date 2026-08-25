package ca.gc.aafc.dina.export.api.generator.helper;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
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

    // 4. Simple path navigation. To-many contexts (arrays) collect the value
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

}

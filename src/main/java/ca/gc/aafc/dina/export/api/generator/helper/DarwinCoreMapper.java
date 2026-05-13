package ca.gc.aafc.dina.export.api.generator.helper;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;

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

    // 3. Path with filter (for arrays like determinations, geoReferenceAssertions)
    if (mapping.getFilter() != null) {
      JsonNode filtered = filterAndExtract(
        contextNode,
        mapping.getSource(),
        mapping.getFilter(),
        mapping.getPath()
      );
      return filtered != null ? filtered.asText() : null;
    }

    // 4. Simple path navigation
    JsonNode value = navigateToSource(contextNode, mapping.getSource());
    return value != null ? value.asText() : null;
  }

  /**
   * Navigate a dot-notation path within a JsonNode
   *
   * Handles paths like:
   * - "id" (direct field)
   * - "collection.code" (nested object)
   *
   * Returns the node itself if path is null/empty.
   *
   * @param root The root JsonNode
   * @param path Dot-notation path (e.g., "collection.code")
   * @return The JsonNode at the path, or null if not found
   */
  private JsonNode navigateToSource(JsonNode root, String path) {
    if (path == null || path.isEmpty() || root == null) {
      return root;
    }

    JsonNode current = root;
    String[] segments = path.split("\\.");

    for (String segment : segments) {
      if (current == null) {
        return null;
      }
      current = current.get(segment);
    }

    return current;
  }

  /**
   * Extract from an array by filtering and optionally getting nested field
   *
   * Process:
   * 1. Navigate to source (should be an array)
   * 2. Find first element matching filter
   * 3. If path specified, navigate to that field within the matched element
   * 4. Otherwise return the matched element itself
   *
   * Example:
   * - source: "determination" (array)
   * - filter: "isPrimary == true" (find primary)
   * - path: null (return the whole determination object)
   * Result: The primary determination object
   *
   * Another example:
   * - source: "geoReferenceAssertions" (array)
   * - filter: "isPrimary == true"
   * - path: "stateProvince" (get stateProvince from matched element)
   * Result: "Ontario"
   *
   * @param root The context node (usually pointing to an array)
   * @param source The field name containing the array
   * @param filter Filter condition (e.g., "isPrimary == true")
   * @param path Optional nested path within matched element
   * @return The extracted value, or null if no match
   */
  private JsonNode filterAndExtract(JsonNode root, String source, String filter, String path) {
    // Navigate to the array
    JsonNode sourceArray = navigateToSource(root, source);

    if (sourceArray == null || !sourceArray.isArray()) {
      log.debug("Source is not an array or not found: {}", source);
      return null;
    }

    // Find first element matching filter
    for (JsonNode item : sourceArray) {
      if (evaluateFilter(item, filter)) {
        // If path specified, navigate to it within the filtered item
        if (path != null && !path.isEmpty()) {
          return navigateToSource(item, path);
        }
        // Otherwise return the filtered item itself
        return item;
      }
    }

    log.debug("No array element matched filter: {}", filter);
    return null;
  }

  /**
   * Evaluate a filter condition on a JsonNode
   *
   * Supports: "fieldName == value"
   *
   * Comparison is done as text (asText()).
   *
   * Examples:
   * - "isPrimary == true" → true/false boolean
   * - "status == active" → string
   * - "rank == species" → string
   *
   * @param node The node to evaluate
   * @param filter Filter expression (e.g., "isPrimary == true")
   * @return true if filter matches, false otherwise
   */
  private boolean evaluateFilter(JsonNode node, String filter) {
    if (node == null || !node.isObject()) {
      return false;
    }

    // Parse filter: "fieldName == value"
    String[] parts = filter.split("==");
    if (parts.length != 2) {
      log.warn("Invalid filter format (expected 'field == value'): {}", filter);
      return false;
    }

    String fieldName = parts[0].trim();
    String expectedValue = parts[1].trim();

    // Get field value from node
    JsonNode fieldValue = node.get(fieldName);
    if (fieldValue == null || fieldValue.isNull()) {
      return false;
    }

    // Compare as text
    String actualValue = fieldValue.asText();
    return expectedValue.equals(actualValue);
  }
}

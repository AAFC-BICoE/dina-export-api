package ca.gc.aafc.dina.export.api.generator.helper;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j2;

/**
 * Builds the entities context map from materialSample
 *
 * Uses entity hierarchy defined in YAML (darwincore-mapping.yaml)
 *
 * Process:
 * 1. For each defined entityContext in YAML
 * 2. Navigate from materialSample or organism to reach it
 * 3. Apply filter if present (e.g., isPrimary == true)
 * 4. Add to context map for use by DarwinCoreMapper
 *
 * Result: A map where keys are context names (e.g., "determination", "geoReferenceAssertions")
 * and values are the actual JsonNode objects to extract from
 */
@Component
@Log4j2
public class DarwinCoreContextBuilder {

  private final DarwinCoreExportConfig config;

  public DarwinCoreContextBuilder(DarwinCoreExportConfig config) {
    this.config = config;
  }

  /**
   * Build complete resource context map from materialSample
   *
   * Builds contexts in order, allowing dependent contexts to reference
   * previously built contexts.
   *
   * @param materialSample The root materialSample entity (from JSON:API /data)
   * @return Map of all resources keyed by context name
   *
   * Example result:
   * {
   *   "materialSample": materialSample_node,
   *   "organism": primary_organism_node,
   *   "collectingEvent": collectingEvent_node,
   *   "determination": primary_determination_node,
   *   "geoReferenceAssertions": primary_georef_node,
   * }
   */
  public Map<String, JsonNode> buildContextMap(JsonNode materialSample) {
    Map<String, JsonNode> contextMap = new HashMap<>();
    Map<String, DarwinCoreExportConfig.ResourceContext> resourceContexts = config.getOccurrence().getResourceContexts();

    if (resourceContexts == null || resourceContexts.isEmpty()) {
      log.warn("No resourceContexts defined in DarwinCore config");
      return contextMap;
    }

    // Build contexts in order (LinkedHashMap preserves order)
    for (Map.Entry<String, DarwinCoreExportConfig.ResourceContext> entry : resourceContexts.entrySet()) {
      String contextName = entry.getKey();
      DarwinCoreExportConfig.ResourceContext resourceContext = entry.getValue();

      JsonNode contextNode = null;

      if (resourceContext.isRoot()) {
        // Root resource is materialSample itself
        contextNode = materialSample;
      } else {
        // Get parent context
        String parentContextName = resourceContext.getContext();
        JsonNode parentContext = contextMap.get(parentContextName);

        if (parentContext == null) {
          log.warn("Parent context not found: {} for {}", parentContextName, contextName);
          continue;
        }

        // Navigate from parent context
        contextNode = navigateAndFilter(parentContext, resourceContext.getPath(),
          resourceContext.getFilter());
      }

      if (contextNode != null) {
        contextMap.put(contextName, contextNode);
        log.debug("Resolved context: {}", contextName);
      } else {
        log.debug("Could not resolve context: {}", contextName);
      }
    }

    return contextMap;
  }

  /**
   * Navigate from a parent context and apply filter if needed
   *
   * @param parentContext The parent context node
   * @param path The path to navigate from parent (e.g., "determination")
   * @param filter Optional filter to apply (e.g., "isPrimary == true")
   * @return The resolved and filtered JsonNode, or null if not found
   */
  private JsonNode navigateAndFilter(JsonNode parentContext, String path, String filter) {
    if (path == null || path.isEmpty()) {
      return parentContext;
    }

    // Navigate through path segments
    JsonNode current = parentContext;
    String[] segments = path.split("\\.");

    for (String segment : segments) {
      if (current == null) {
        return null;
      }
      current = current.get(segment);
    }

    if (current == null) {
      return null;
    }

    // Apply filter if this is an array
    if (current.isArray() && filter != null) {
      current = applyFilter(current, filter);
    }

    return current;
  }

  /**
   * Apply filter to an array to select a specific element
   *
   * @param arrayNode The array JsonNode
   * @param filter Filter condition (e.g., "isPrimary == true")
   * @return The first matching element, or null if no match
   */
  private JsonNode applyFilter(JsonNode arrayNode, String filter) {
    if (!arrayNode.isArray()) {
      return arrayNode;
    }

    for (JsonNode item : arrayNode) {
      if (evaluateFilter(item, filter)) {
        log.debug("Filter '{}' matched", filter);
        return item;
      }
    }

    log.debug("No array element matched filter: {}", filter);
    return null;
  }

  /**
   * Evaluate a filter condition on a JsonNode
   *
   * @param node The node to evaluate
   * @param filter Filter expression (e.g., "isPrimary == true")
   * @return true if filter matches, false otherwise
   */
  private boolean evaluateFilter(JsonNode node, String filter) {
    if (node == null || !node.isObject()) {
      return false;
    }

    String[] parts = filter.split("==");
    if (parts.length != 2) {
      log.warn("Invalid filter format: {}", filter);
      return false;
    }

    String field = parts[0].trim();
    String expectedValue = parts[1].trim();

    JsonNode fieldValue = node.get(field);
    if (fieldValue == null || fieldValue.isNull()) {
      return false;
    }

    String actualValue = fieldValue.asText();
    return expectedValue.equals(actualValue);
  }
}

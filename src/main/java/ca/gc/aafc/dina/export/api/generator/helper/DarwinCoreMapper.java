package ca.gc.aafc.dina.export.api.generator.helper;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

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

    // 3. Classification-path extraction (e.g., kingdom from scientificNameDetails)
    if (mapping.getClassificationRank() != null) {
      return extractClassificationRank(contextNode, mapping.getClassificationRank());
    }

    // 4. Filter + optional subpath (JSONPath filter syntax, e.g. @.placeType == 'county')
    if (mapping.getFilter() != null) {
      String expression = "$." + mapping.getSource() + "[?(" + mapping.getFilter() + ")]"
          + (mapping.getPath() != null ? "." + mapping.getPath() : "");
      JsonNode result = JsonHelper.findOneInJsonNode(contextNode, expression);
      return result != null ? result.asText() : null;
    }

    // 5. Simple definite-path navigation
    JsonNode value = JsonHelper.findOneInJsonNode(contextNode, "$." + mapping.getSource());
    return value != null ? value.asText() : null;
  }

  /**
   * Extract a classification rank value from a determination node.
   *
   * Uses {@link JsonHelper#findOneInJsonNode} to reach the two parallel pipe-delimited strings
   * in {@code scientificNameDetails}, then resolves the rank positionally.
   *
   * Example for rankName="kingdom" given:
   *   classificationRanks: "domain|kingdom|phylum|..."
   *   classificationPath:  "Eukaryota|Animalia|Chordata|..."
   * → "Animalia"
   *
   * @param contextNode the determination JsonNode
   * @param rankName    the rank to look up (e.g., "kingdom")
   * @return the rank value, or null if the rank is absent
   */
  private String extractClassificationRank(JsonNode contextNode, String rankName) {
    JsonNode details = JsonHelper.findOneInJsonNode(contextNode, "$.scientificNameDetails");
    if (details == null) {
      log.debug("scientificNameDetails missing for rank: {}", rankName);
      return null;
    }

    JsonNode ranksNode = details.get("classificationRanks");
    JsonNode pathNode  = details.get("classificationPath");

    if (ranksNode == null || ranksNode.isNull() || pathNode == null || pathNode.isNull()) {
      log.debug("classificationRanks or classificationPath missing for rank: {}", rankName);
      return null;
    }

    String[] rankArray  = ranksNode.asText().split("\\|", -1);
    String[] valueArray = pathNode.asText().split("\\|", -1);

    for (int i = 0; i < rankArray.length; i++) {
      if (rankName.equals(rankArray[i].trim()) && i < valueArray.length) {
        return valueArray[i];
      }
    }

    log.debug("Rank '{}' not found in classificationRanks: {}", rankName, ranksNode.asText());
    return null;
  }
}

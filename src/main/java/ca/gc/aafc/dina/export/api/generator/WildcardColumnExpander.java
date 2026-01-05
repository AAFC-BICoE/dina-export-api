package ca.gc.aafc.dina.export.api.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;
import ca.gc.aafc.dina.json.JsonHelper;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import lombok.extern.log4j.Log4j2;

/**
 * Helper class to expand wildcard columns by processing records from Elasticsearch.
 * Wildcards are indicated by columns ending with ".*" 
 * (e.g., "collectingEvent.extensionValues.mixs_soil_v5.*")
 */
@Log4j2
public class WildcardColumnExpander {

  private final String sourceIndex;
  private final String query;
  private final List<String> columns;
  private final List<String> aliases;
  private final ElasticSearchDataSource elasticSearchDataSource;
  private final ObjectMapper objectMapper;
  private final BiConsumer<ObjectNode, JsonNode> recordProcessor;

  /**
   * Creates a new WildcardColumnExpander.
   *
   * @param sourceIndex the Elasticsearch index to query
   * @param query the query to execute
   * @param columns the list of columns (will be modified in place)
   * @param aliases the list of aliases (will be modified in place, can be null)
   * @param elasticSearchDataSource the data source for Elasticsearch queries
   * @param objectMapper the Jackson ObjectMapper
   * @param recordProcessor a function that processes a record to flatten relationships
   */
  public WildcardColumnExpander(String sourceIndex, String query, 
                                 List<String> columns, List<String> aliases,
                                 ElasticSearchDataSource elasticSearchDataSource,
                                 ObjectMapper objectMapper,
                                 BiConsumer<ObjectNode, JsonNode> recordProcessor) {
    this.sourceIndex = sourceIndex;
    this.query = query;
    this.columns = columns;
    this.aliases = aliases;
    this.elasticSearchDataSource = elasticSearchDataSource;
    this.objectMapper = objectMapper;
    this.recordProcessor = recordProcessor;
  }

  /**
   * Expands wildcard columns by fetching multiple records and examining their flattened fields.
   * Collects fields from up to 10 records to ensure comprehensive field discovery.
   */
  public void expandWildcards() throws IOException {
    SearchResponse<JsonNode> response = elasticSearchDataSource.searchWithPIT(sourceIndex, query);

    if (response.hits().hits().isEmpty()) {
      log.warn("No records found to expand wildcards");
      return;
    }

    // Collect all unique field names from multiple records
    Set<String> availableFields = new LinkedHashSet<>();
    int recordsToSample = Math.min(10, response.hits().hits().size());

    for (int i = 0; i < recordsToSample; i++) {
      Hit<JsonNode> hit = response.hits().hits().get(i);
      JsonNode record = hit.source();

      Optional<JsonNode> attributes = JsonHelper.atJsonPtr(record, JSONApiDocumentStructure.ATTRIBUTES_PTR);
      if (attributes.isEmpty() || !(attributes.get() instanceof ObjectNode attributeObjNode)) {
        continue;
      }

      attributeObjNode.put(JSONApiDocumentStructure.ID, hit.id());
      
      // Use the provided processor to flatten the record
      recordProcessor.accept(attributeObjNode, record);

      // Add all field names from this record
      Iterator<String> fieldNames = attributeObjNode.fieldNames();
      fieldNames.forEachRemaining(availableFields::add);
    }

    // Close the PIT since we're done sampling
    elasticSearchDataSource.closePointInTime(response.pitId());

    log.info("Sampled {} records for wildcard expansion, found {} unique fields",
        recordsToSample, availableFields.size());

    // Expand wildcards in columns
    List<String> expandedColumns = new ArrayList<>();
    List<String> expandedAliases = aliases != null ? new ArrayList<>() : null;

    for (int i = 0; i < columns.size(); i++) {
      String column = columns.get(i);
      String alias = aliases != null && i < aliases.size() ? aliases.get(i) : "";

      if (column.endsWith(".*")) {
        // This is a wildcard - expand it
        String prefix = column.substring(0, column.length() - 1); // Remove the '*'
        List<String> matchingFields = availableFields.stream()
            .filter(field -> field.startsWith(prefix))
            .sorted()
            .toList();

        if (matchingFields.isEmpty()) {
          log.warn("Wildcard column '{}' matched no fields", column);
        } else {
          log.info("Expanded wildcard '{}' to {} fields", column, matchingFields.size());
          expandedColumns.addAll(matchingFields);

          if (expandedAliases != null) {
            for (String field : matchingFields) {
              if (StringUtils.isNotBlank(alias)) {
                String suffix = field.substring(prefix.length());
                expandedAliases.add(alias + suffix);
              } else {
                expandedAliases.add(field);
              }
            }
          }
        }
      } else {
        expandedColumns.add(column);
        if (expandedAliases != null) {
          expandedAliases.add(alias);
        }
      }
    }

    // Replace the original columns and aliases with expanded versions
    columns.clear();
    columns.addAll(expandedColumns);

    if (aliases != null && expandedAliases != null) {
      aliases.clear();
      aliases.addAll(expandedAliases);
    }
  }
}

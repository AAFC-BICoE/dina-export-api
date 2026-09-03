package ca.gc.aafc.dina.export.api.config;

import lombok.Data;

/**
 * Configuration for resolving a value through an external DINA API reference
 * (e.g. a controlled-vocabulary-item).
 *
 * <p>Used by exporters (DarwinCore, Tabular, ...) to map values found in a source document
 * (e.g. managed attribute values) to a value stored on a referenced document fetched from
 * an external DINA API. Kept as its own top-level type so it is not coupled to a specific
 * export configuration.
 */
@Data
public class ApiReference {
  private String vocabularyValue;       // Field to extract from the referenced document (e.g. "uriTemplate")
  private String vocabularyKey;         // Optional: restrict resolution to this managed attribute key (e.g. "ena_run_accession")
  private String valuePlaceholder = "$1"; // Placeholder in vocabularyValue replaced with the managed attribute value
  private String vocabularyUrl;         // Base URL of the referenced resource, e.g. "${dina.export.objectStoreApiUrl}/controlled-vocabulary-item"; filter[key] and dinaComponent query params are appended by the resolver
  private String dinaComponent;         // dinaComponent filter value used when querying the referenced resource
}

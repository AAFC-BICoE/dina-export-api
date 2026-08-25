package ca.gc.aafc.dina.export.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * DarwinCore export configuration
 * From darwincore-mapping.yaml
 */
@PropertySource("classpath:dwc/darwincore-mapping.yaml")
@ConfigurationProperties(prefix = "dwc")
@Validated
@Data
public class DarwinCoreExportConfig {

  private Core core;

  /**
   * Core export configuration
   */
  @Data
  public static class Core {
    private String rowType;
    private String entityType;
    private List<String> fileLocations;
    private Map<String, ResourceContext> resourceContexts;

    /**
     * Column mappings from DINA attributes to DarwinCore terms
     */
    private List<ColumnMapping> columns;
  }

  @Data
  public static class ResourceContext {
    private boolean root;             // true if this is materialSample (the root)
    private String context;           // Parent context to navigate from (null for materialSample)
    private String path;              // Path from root to reach this entity (null for root)
    private String filter;            // Filter to select from array (e.g., "isPrimary == true")
  }

  @Data
  public static class ColumnMapping {
    private String dwcTerm;           // DarwinCore term name
    private String termUri;           // Full URI for the term
    private String context;           // Entity context: "organism", "materialSample"
    private String source;            // JSONPath relative to context
    private String filter;            // Filter condition (e.g., "isPrimary == true")
    private String path;              // Additional path after filtering (optional)
    private String staticValue;       // Static value (optional)
    private String dataType = "string";
    private String separator = " | ";   // Separator for array values
    private boolean required;}
}

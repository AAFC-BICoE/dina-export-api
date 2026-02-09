package ca.gc.aafc.dina.export.api.dto;

import ca.gc.aafc.dina.dto.RelatedEntity;
import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.export.api.entity.DataExport;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.toedter.spring.hateoas.jsonapi.JsonApiId;
import com.toedter.spring.hateoas.jsonapi.JsonApiTypeForClass;

@RelatedEntity(DataExport.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass(DataExportDto.TYPENAME)
public class DataExportDto implements ca.gc.aafc.dina.dto.JsonApiResource {

  public static final String TYPENAME = "data-export";

  @JsonApiId
  private UUID uuid;

  private DataExport.ExportStatus status;
  private OffsetDateTime createdOn;
  private String createdBy;

  private String name;
  private DataExport.ExportType exportType;
  private Map<String, String> exportOptions;

  private String source;

  private String query;

  // will be removed in 0.19
  private List<String> columns;
  
  /**
   * Column aliases for headers.
   * Supports nested map (for multi-entity).
   * - Nested map: {"materialSample": ["Sample Name", "ID"], "collectingEvent": ["Location", "Event ID"]}
   */
  private Object columnAliases;

  /**
   * Schema-based column configuration for exports.
   * Unified field that handles multi-entity exports.
   * - Multi-entity: {"materialSample": ["materialSampleName", "id"], "collectingEvent": ["dwcVerbatimLocality", "id"]}
   * Uses LinkedHashMap to preserve entity order - first entity is primary.
   */
  private LinkedHashMap<String, List<String>> schema;

  private Map<String, DataExportFunction> functions;

  // will be removed in 0.18
  private Map<String, DataExport.FunctionDef> columnFunctions;

  @Override
  @JsonIgnore
  public String getJsonApiType() {
    return TYPENAME;
  }

  @Override
  @JsonIgnore
  public UUID getJsonApiId() {
    return uuid;
  }
}

package ca.gc.aafc.dina.export.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.toedter.spring.hateoas.jsonapi.JsonApiId;
import com.toedter.spring.hateoas.jsonapi.JsonApiTypeForClass;

import ca.gc.aafc.dina.dto.JsonApiResource;
import ca.gc.aafc.dina.dto.RelatedEntity;
import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.entity.DataExportTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RelatedEntity(DataExportTemplate.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass(DataExportTemplateDto.TYPENAME)
public class DataExportTemplateDto implements JsonApiResource {

  public static final String TYPENAME = "data-export-template";

  @JsonApiId
  private UUID uuid;

  private OffsetDateTime createdOn;
  private String createdBy;
  private String group;

  private Boolean restrictToCreatedBy;
  private Boolean publiclyReleasable;

  private String name;
  private DataExport.ExportType exportType;
  private Map<String, String> exportOptions;

  private String[] columns;
  private String[] columnAliases;

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

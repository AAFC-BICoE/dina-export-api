package ca.gc.aafc.dina.export.api.dto;

import com.toedter.spring.hateoas.jsonapi.JsonApiId;
import com.toedter.spring.hateoas.jsonapi.JsonApiTypeForClass;

import ca.gc.aafc.dina.export.api.entity.DataExport;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@JsonApiTypeForClass(DataExportTemplateDto.TYPENAME)
@Data
public class DataExportTemplateDto {

  public static final String TYPENAME = "data-export-template";

  @JsonApiId
  private UUID uuid;

  private DataExport.ExportStatus status;
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
  private Map<String, DataExport.FunctionDef> columnFunctions;

}

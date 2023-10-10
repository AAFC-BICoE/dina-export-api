package ca.gc.aafc.dina.export.api.dto;

import ca.gc.aafc.dina.dto.RelatedEntity;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.mapper.CustomFieldAdapter;
import ca.gc.aafc.dina.mapper.IgnoreDinaMapping;

import io.crnk.core.resource.annotations.JsonApiId;
import io.crnk.core.resource.annotations.JsonApiResource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RelatedEntity(DataExport.class)
@CustomFieldAdapter(adapters = {
  FieldsAdapter.DataExportQueryFieldAdapter.class,
  FieldsAdapter.DataExportColumnsFieldAdapter.class
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonApiResource(type = DataExportDto.TYPENAME)
public class DataExportDto {

  public static final String TYPENAME = "data-export";

  @JsonApiId
  private UUID uuid;

  private DataExport.ExportStatus status;
  private OffsetDateTime createdOn;
  private String createdBy;

  private String source;

  @IgnoreDinaMapping(reason = "handled by DataExportQueryFieldAdapter")
  private String query;

  @IgnoreDinaMapping(reason = "handled by DataExportColumnsFieldAdapter")
  private List<String> columns;

}

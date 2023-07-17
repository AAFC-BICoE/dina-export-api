package ca.gc.aafc.dina.export.api.dto;

import io.crnk.core.resource.annotations.JsonApiId;
import io.crnk.core.resource.annotations.JsonApiResource;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonApiResource(type = ExportRequestDto.TYPENAME)
public class ExportRequestDto {

  public static final String TYPENAME = "export-request";

  @JsonApiId
  private UUID uuid;

  private String source;
  private String query;
  private List<String> columns;

}

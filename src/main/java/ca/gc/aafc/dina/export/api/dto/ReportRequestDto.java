package ca.gc.aafc.dina.export.api.dto;

import io.crnk.core.resource.annotations.JsonApiId;
import io.crnk.core.resource.annotations.JsonApiResource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonApiResource(type = ReportRequestDto.TYPENAME)
public class ReportRequestDto {

  public static final String TYPENAME = "report-request";

  @JsonApiId
  private UUID uuid;
  private String group;

  private UUID reportTemplateUUID;

  private Map<String, Object> payload;

}

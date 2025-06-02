package ca.gc.aafc.dina.export.api.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

import com.toedter.spring.hateoas.jsonapi.JsonApiId;
import com.toedter.spring.hateoas.jsonapi.JsonApiTypeForClass;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonApiTypeForClass(DataExportDto.TYPENAME)
public class ReportRequestDto implements ca.gc.aafc.dina.dto.JsonApiResource {

  public static final String TYPENAME = "report-request";

  @JsonApiId
  private UUID uuid;
  private String group;

  private UUID reportTemplateUUID;

  private Map<String, Object> payload;

  @Override
  public String getJsonApiType() {
    return TYPENAME;
  }

  @Override
  public UUID getJsonApiId() {
    return uuid;
  }
}

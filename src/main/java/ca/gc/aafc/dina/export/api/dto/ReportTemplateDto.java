package ca.gc.aafc.dina.export.api.dto;

import ca.gc.aafc.dina.dto.RelatedEntity;
import ca.gc.aafc.dina.export.api.entity.ReportTemplate;
import ca.gc.aafc.dina.i18n.MultilingualDescription;

import io.crnk.core.resource.annotations.JsonApiId;
import io.crnk.core.resource.annotations.JsonApiResource;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.PropertyName;
import org.javers.core.metamodel.annotation.TypeName;

@RelatedEntity(ReportTemplate.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName(ReportTemplateDto.TYPENAME)
@JsonApiResource(type = ReportTemplateDto.TYPENAME)
public class ReportTemplateDto {

  public static final String TYPENAME = "report-template";

  @Id
  @PropertyName("id")
  @JsonApiId
  private UUID uuid;
  private String group;

  private OffsetDateTime createdOn;
  private String createdBy;

  private String name;
  private ReportTemplate.ReportType reportType;
  private MultilingualDescription multilingualDescription;

  private String templateFilename;

  // can be an intermediate media type like HTML or JSON that will then be transformed in PDF or CSV
  private String templateOutputMediaType;
  private String outputMediaType;

  private String[] reportVariables;

  @Builder.Default
  private Boolean includesBarcode = false;

}

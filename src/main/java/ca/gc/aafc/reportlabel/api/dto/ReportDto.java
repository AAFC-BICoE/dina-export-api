package ca.gc.aafc.reportlabel.api.dto;

import ca.gc.aafc.dina.dto.RelatedEntity;
import ca.gc.aafc.dina.i18n.MultilingualDescription;
import ca.gc.aafc.reportlabel.api.entity.Report;

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

@RelatedEntity(Report.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName(ReportDto.TYPENAME)
@JsonApiResource(type = ReportDto.TYPENAME)
public class ReportDto {

  public static final String TYPENAME = "report";

  @Id
  @PropertyName("id")
  @JsonApiId
  private UUID uuid;
  private String group;

  private OffsetDateTime createdOn;
  private String createdBy;

  private String name;
  private MultilingualDescription multilingualDescription;

  private String templateFilename;
  private String outputMediaType;

  @Builder.Default
  private Boolean includesBarcode = false;

}

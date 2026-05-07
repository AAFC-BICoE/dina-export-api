package ca.gc.aafc.dina.export.api.entity;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;

import ca.gc.aafc.dina.entity.DinaEntity;
import ca.gc.aafc.dina.i18n.MultilingualDescription;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@Setter
@Getter
@Builder
@RequiredArgsConstructor
public class ReportTemplate implements DinaEntity {

  public enum ReportType { MATERIAL_SAMPLE_LABEL, STORAGE_LABEL }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @NaturalId
  @NotNull
  @Column(name = "uuid", unique = true)
  private UUID uuid;

  @NotBlank
  @Column(name = "_group")
  @Size(max = 250)
  private String group;

  @Column(name = "created_on", insertable = false, updatable = false)
  @Generated(value = GenerationTime.INSERT)
  private OffsetDateTime createdOn;

  @NotBlank
  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @NotBlank
  @Size(max = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @NotNull
  private ReportType reportType;

  @Type(JsonType.class)
  @Column(name = "multilingual_description", columnDefinition = "jsonb")
  private MultilingualDescription multilingualDescription;

  @NotBlank
  @Size(max = 100)
  private String templateFilename;

  // can be an intermediate media type like HTML or JSON that will then be transformed in PDF or CSV
  @NotBlank
  @Size(max = 100)
  private String templateOutputMediaType;

  @NotBlank
  @Size(max = 100)
  private String outputMediaType;

  @NotNull
  @Builder.Default
  private Boolean includesBarcode = false;

  @Column
  private String[] reportVariables;

}

package ca.gc.aafc.dina.export.api.entity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Transient;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;

import ca.gc.aafc.dina.entity.DinaEntity;

/**
 * DataExportTemplate represents a template to create {@link DataExport}.
 */
@Entity
@AllArgsConstructor
@Setter
@Getter
@Builder
@RequiredArgsConstructor
public class DataExportTemplate implements DinaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @NaturalId
  @NotNull
  @Column(name = "uuid", unique = true)
  private UUID uuid;

  @NotBlank
  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @Size(max = 100)
  private String name;

  @NotBlank
  @Size(max = 50)
  @Column(name = "_group")
  private String group;

  /**
   * Template can only be used (read) by the user defined by the createdBy attribute.
   * publiclyReleasable must be false
   */
  @NotNull
  private Boolean restrictToCreatedBy = false;

  /**
   * Can the template be used (read) by users that are not in the group ?
   * restrictToCreatedBy must be false
   */
  @NotNull
  private Boolean publiclyReleasable = false;

  @Enumerated(EnumType.STRING)
  @NotNull
  @Column
  private DataExport.ExportType exportType;

  /**
   * Options specific to the type
   */
  @Column
  @Type(type = "jsonb")
  private Map<String, String> exportOptions;

  @Type(type = "string-array")
  @Column
  private String[] columns;

  @Type(type = "string-array")
  @Column
  private String[] columnAliases;

  @Type(type = "jsonb")
  @Column
  private Map<String, DataExport.FunctionDef> columnFunctions;

  @Column(name = "created_on", insertable = false, updatable = false)
  @Generated(value = GenerationTime.INSERT)
  private OffsetDateTime createdOn;

  /**
   * Return publiclyReleasable as Optional as defined by
   * {@link ca.gc.aafc.dina.entity.DinaEntity}.
   *
   * @return
   */
  @Override
  @Transient
  public Optional<Boolean> isPubliclyReleasable() {
    return Optional.ofNullable(publiclyReleasable);
  }

}

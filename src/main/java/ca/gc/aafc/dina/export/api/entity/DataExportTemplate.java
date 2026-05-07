package ca.gc.aafc.dina.export.api.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import ca.gc.aafc.dina.export.api.config.DataExportFunction;

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
  @Builder.Default
  private Boolean restrictToCreatedBy = false;

  /**
   * Can the template be used (read) by users that are not in the group ?
   * restrictToCreatedBy must be false
   */
  @NotNull
  @Builder.Default
  private Boolean publiclyReleasable = false;

  @Enumerated(EnumType.STRING)
  @NotNull
  @Column
  private DataExport.ExportType exportType;

  /**
   * Options specific to the type
   */
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, String> exportOptions;

  /**
   * Schema-based column configuration for exports.
   * Unified field that handles multi-entity exports with columns and optional aliases per entity.
   * See {@link DataExport#schema} for format details.
   */
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private LinkedHashMap<String, DataExportSchemaEntry> schema;

  // functions by column
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, DataExportFunction> functions;

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

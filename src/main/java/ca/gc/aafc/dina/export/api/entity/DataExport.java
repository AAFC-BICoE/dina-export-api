package ca.gc.aafc.dina.export.api.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * Data export represents a single file export. The file can be a package.
 */
@Entity
@AllArgsConstructor
@Setter
@Getter
@Builder
@RequiredArgsConstructor
public class DataExport implements DinaEntity {

  public enum ExportStatus { NEW, RUNNING, COMPLETED, EXPIRED, ERROR }
  public enum ExportType { TABULAR_DATA, OBJECT_ARCHIVE }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @NaturalId
  @NotNull
  @Column(name = "uuid", unique = true)
  private UUID uuid;

  @Column(name = "created_on", insertable = false, updatable = false)
  @Generated(value = GenerationTime.INSERT)
  private OffsetDateTime createdOn;

  @NotBlank
  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @Size(max = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @NotNull
  @Column
  private ExportType exportType;

  /**
   * Filename including extension
   */
  @NotNull
  @Size(max = 100)
  @Column
  private String filename;

  /**
   * Options specific to the type
   */
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, String> exportOptions;

  /**
   * Source of the query (e.g. the ElasticSearch index)
   */
  @NotBlank
  @Size(max = 100)
  private String source;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> query;

  /**
   * Schema-based column configuration for exports.
   * Unified field that handles multi-entity exports with columns and optional aliases per entity.
   * Example:
   * <pre>
   * {
   *   "material-sample": {
   *     "columns": ["id", "materialSampleName"],
   *     "aliases": ["Sample ID", "Sample Name"]
   *   },
   *   "collecting-event": {
   *     "columns": ["dwcVerbatimLocality"],
   *     "aliases": ["Location"]
   *   }
   * }
   * </pre>
   * Uses LinkedHashMap to preserve entity order - first entity is primary.
   */
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private LinkedHashMap<String, DataExportSchemaEntry> schema;

  // functions by column
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, DataExportFunction> functions;

  @Enumerated(EnumType.STRING)
  @NotNull
  @Column
  private ExportStatus status;

  @Transient
  private Map<String, String> transitiveData;

}

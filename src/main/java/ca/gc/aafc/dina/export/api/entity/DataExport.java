package ca.gc.aafc.dina.export.api.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

  // to be replaced by DataExportFunction
  public enum FunctionName { CONCAT, CONVERT_COORDINATES_DD }

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
  @Column
  @Type(type = "jsonb")
  private Map<String, String> exportOptions;

  /**
   * Source of the query (e.g. the ElasticSearch index)
   */
  @NotBlank
  @Size(max = 100)
  private String source;

  @Type(type = "jsonb")
  @Column
  private Map<String, Object> query;

  @Type(type = "string-array")
  @Column
  private String[] columns;

  @Type(type = "string-array")
  @Column
  private String[] columnAliases;

  // to be replaced by functions
  @Type(type = "jsonb")
  @Column
  private Map<String, FunctionDef> columnFunctions;

  // functions by column
  @Type(type = "jsonb")
  @Column
  private Map<String, DataExportFunction> functions;

  @Enumerated(EnumType.STRING)
  @NotNull
  @Column
  private ExportStatus status;

  @Transient
  private Map<String, String> transitiveData;

  public record FunctionDef(FunctionName functionName, List<String> params) {
  }
}

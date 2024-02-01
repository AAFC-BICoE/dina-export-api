package ca.gc.aafc.dina.export.api.entity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
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
 * Data export represents a single file export. The file can be a package.
 */
@Entity
@AllArgsConstructor
@Setter
@Getter
@Builder
@RequiredArgsConstructor
public class DataExport implements DinaEntity {

  public enum ExportStatus { NEW, RUNNING, COMPLETED, ERROR }
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

  @NotNull
  @Column
  private ExportType exportType;

  @NotBlank
  @Size(max = 100)
  private String source;

  @Type(type = "jsonb")
  @Column
  private Map<String, Object> query;

  @Type(type = "string-array")
  @Column
  private String[] columns;

  @Column
  private ExportStatus status;

  @Transient
  private Map<String, String> transitiveData;

}

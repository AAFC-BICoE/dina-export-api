package ca.gc.aafc.reportlabel.api.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;

import javax.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import ca.gc.aafc.dina.entity.DinaEntity;
import ca.gc.aafc.dina.i18n.MultilingualDescription;

@Entity
@AllArgsConstructor
@Setter
@Getter
@Builder
@RequiredArgsConstructor
public class Report implements DinaEntity {

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

  @Type(type = "jsonb")
  @Column(name = "multilingual_description")
  private MultilingualDescription multilingualDescription;

}

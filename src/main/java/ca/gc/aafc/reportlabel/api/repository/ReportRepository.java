package ca.gc.aafc.reportlabel.api.repository;

import java.util.Optional;

import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.mapper.DinaMapper;
import ca.gc.aafc.dina.repository.DinaRepository;
import ca.gc.aafc.dina.security.DinaAuthenticatedUser;
import ca.gc.aafc.dina.security.DinaAuthorizationService;
import ca.gc.aafc.dina.service.AuditService;
import ca.gc.aafc.reportlabel.api.dto.ReportDto;
import ca.gc.aafc.reportlabel.api.entity.ReportTemplate;
import ca.gc.aafc.reportlabel.api.service.ReportService;

@Repository
public class ReportRepository extends DinaRepository<ReportDto, ReportTemplate> {

  private final DinaAuthenticatedUser dinaAuthenticatedUser;

  public ReportRepository(
    ReportService dinaService,
    DinaAuthorizationService groupAuthorizationService,
    AuditService auditService,
    BuildProperties buildProperties,
    Optional<DinaAuthenticatedUser> dinaAuthenticatedUser,
    ObjectMapper objectMapper
  ) {
    super(
      dinaService,
      groupAuthorizationService,
      Optional.of(auditService),
      new DinaMapper<>(ReportDto.class),
      ReportDto.class,
      ReportTemplate.class,
      null,
      null,
      buildProperties, objectMapper);
    this.dinaAuthenticatedUser = dinaAuthenticatedUser.orElse(null);
  }

  @Override
  public <S extends ReportDto> S create(S resource) {
    if (dinaAuthenticatedUser != null) {
      resource.setCreatedBy(dinaAuthenticatedUser.getUsername());
    }
    return super.create(resource);
  }
}

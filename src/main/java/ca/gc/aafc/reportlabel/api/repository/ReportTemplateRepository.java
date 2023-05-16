package ca.gc.aafc.reportlabel.api.repository;

import java.util.Optional;

import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.mapper.DinaMapper;
import ca.gc.aafc.dina.repository.DinaRepository;
import ca.gc.aafc.dina.security.DinaAuthenticatedUser;
import ca.gc.aafc.dina.security.auth.DinaAuthorizationService;
import ca.gc.aafc.dina.service.AuditService;
import ca.gc.aafc.reportlabel.api.dto.ReportTemplateDto;
import ca.gc.aafc.reportlabel.api.entity.ReportTemplate;
import ca.gc.aafc.reportlabel.api.service.ReportTemplateService;

@Repository
public class ReportTemplateRepository extends DinaRepository<ReportTemplateDto, ReportTemplate> {

  private final DinaAuthenticatedUser dinaAuthenticatedUser;

  public ReportTemplateRepository(
    ReportTemplateService dinaService,
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
      new DinaMapper<>(ReportTemplateDto.class),
      ReportTemplateDto.class,
      ReportTemplate.class,
      null,
      null,
      buildProperties, objectMapper);
    this.dinaAuthenticatedUser = dinaAuthenticatedUser.orElse(null);
  }

  @Override
  public <S extends ReportTemplateDto> S create(S resource) {
    if (dinaAuthenticatedUser != null) {
      resource.setCreatedBy(dinaAuthenticatedUser.getUsername());
    }
    return super.create(resource);
  }
}

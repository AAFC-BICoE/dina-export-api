package ca.gc.aafc.dina.export.api.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.validation.SmartValidator;

import ca.gc.aafc.dina.export.api.entity.ReportTemplate;
import ca.gc.aafc.dina.jpa.BaseDAO;
import ca.gc.aafc.dina.service.DefaultDinaService;

@Service
public class ReportTemplateService extends DefaultDinaService<ReportTemplate> {

  public ReportTemplateService(BaseDAO baseDAO,
                               SmartValidator validator) {
    super(baseDAO, validator);
  }

  @Override
  protected void preCreate(ReportTemplate entity) {
    entity.setUuid(UUID.randomUUID());
  }
}

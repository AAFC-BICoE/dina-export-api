package ca.gc.aafc.reportlabel.api.service;

import java.util.UUID;
import lombok.NonNull;

import org.springframework.stereotype.Service;
import org.springframework.validation.SmartValidator;

import ca.gc.aafc.dina.jpa.BaseDAO;
import ca.gc.aafc.dina.service.DefaultDinaService;
import ca.gc.aafc.reportlabel.api.entity.ReportTemplate;

@Service
public class ReportService extends DefaultDinaService<ReportTemplate> {

  public ReportService(@NonNull BaseDAO baseDAO,
                       @NonNull SmartValidator validator) {
    super(baseDAO, validator);
  }

  @Override
  protected void preCreate(ReportTemplate entity) {
    entity.setUuid(UUID.randomUUID());
  }
}

package ca.gc.aafc.reportlabel.api.service;

import java.util.UUID;
import lombok.NonNull;

import org.springframework.stereotype.Service;
import org.springframework.validation.SmartValidator;

import ca.gc.aafc.dina.jpa.BaseDAO;
import ca.gc.aafc.dina.service.DefaultDinaService;
import ca.gc.aafc.reportlabel.api.entity.Report;

@Service
public class ReportService extends DefaultDinaService<Report> {

  public ReportService(@NonNull BaseDAO baseDAO,
                       @NonNull SmartValidator validator) {
    super(baseDAO, validator);
  }

  @Override
  protected void preCreate(Report entity) {
    entity.setUuid(UUID.randomUUID());
  }
}

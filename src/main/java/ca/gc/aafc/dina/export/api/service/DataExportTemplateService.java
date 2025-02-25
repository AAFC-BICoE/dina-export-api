package ca.gc.aafc.dina.export.api.service;

import java.util.UUID;
import lombok.NonNull;

import org.springframework.stereotype.Service;
import org.springframework.validation.SmartValidator;

import ca.gc.aafc.dina.export.api.entity.DataExportTemplate;
import ca.gc.aafc.dina.export.api.validation.DataExportTemplateValidator;
import ca.gc.aafc.dina.jpa.BaseDAO;
import ca.gc.aafc.dina.service.DefaultDinaService;

@Service
public class DataExportTemplateService extends DefaultDinaService<DataExportTemplate> {

  private final DataExportTemplateValidator dataExportTemplateValidator;

  public DataExportTemplateService(@NonNull BaseDAO baseDAO,
                                   DataExportTemplateValidator dataExportTemplateValidator,
                                   @NonNull SmartValidator validator) {
    super(baseDAO, validator);
    this.dataExportTemplateValidator = dataExportTemplateValidator;
  }

  @Override
  public void preCreate(DataExportTemplate dinaExportTemplate) {

    if (dinaExportTemplate.getUuid() == null) {
      dinaExportTemplate.setUuid(UUID.randomUUID());
    }
  }

  @Override
  public void validateBusinessRules(DataExportTemplate entity) {
    applyBusinessRule(entity, dataExportTemplateValidator);
  }

}

package ca.gc.aafc.dina.export.api.service;

import org.junit.jupiter.api.Test;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.entity.DataExportTemplate;
import ca.gc.aafc.dina.export.api.testsupport.factories.DataExportTemplateFactory;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

public class DataExportTemplateServiceIT extends BaseIntegrationTest {

  @Inject
  private DataExportTemplateService dataExportTemplateService;

  @Test
  @Transactional
  public void onCreate_DataExportTemplateCreated() {
    DataExportTemplate dataExportTemplate = DataExportTemplateFactory.newDataExportTemplate()
      .restrictToCreatedBy(true)
      .publiclyReleasable(false)
      .build();

    dataExportTemplateService.create(dataExportTemplate);
  }

}

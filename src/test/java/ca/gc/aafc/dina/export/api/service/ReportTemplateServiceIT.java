package ca.gc.aafc.dina.export.api.service;

import javax.inject.Inject;
import javax.transaction.Transactional;


import org.junit.jupiter.api.Test;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.entity.ReportTemplate;
import ca.gc.aafc.dina.export.api.testsupport.factories.ReportTemplateFactory;

@Transactional
public class ReportTemplateServiceIT extends BaseIntegrationTest {

  @Inject
  private ReportTemplateService reportService;

  @Test
  public void reportService_onCreate_reportEntryCreated() {
    ReportTemplate reportTemplate = ReportTemplateFactory.newReport().build();
    reportService.create(reportTemplate);
  }
}

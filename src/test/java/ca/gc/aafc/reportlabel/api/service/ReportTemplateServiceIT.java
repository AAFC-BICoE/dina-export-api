package ca.gc.aafc.reportlabel.api.service;

import javax.inject.Inject;
import javax.transaction.Transactional;


import org.junit.jupiter.api.Test;

import ca.gc.aafc.reportlabel.api.BaseIntegrationTest;
import ca.gc.aafc.reportlabel.api.entity.ReportTemplate;
import ca.gc.aafc.reportlabel.api.testsupport.factories.ReportTemplateFactory;

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

package ca.gc.aafc.reportlabel.api.service;

import javax.inject.Inject;
import javax.transaction.Transactional;


import org.junit.jupiter.api.Test;

import ca.gc.aafc.reportlabel.api.BaseIntegrationTest;
import ca.gc.aafc.reportlabel.api.entity.Report;
import ca.gc.aafc.reportlabel.api.testsupport.factories.ReportFactory;

@Transactional
public class ReportServiceIT extends BaseIntegrationTest {

  @Inject
  private ReportService reportService;

  @Test
  public void reportService_onCreate_reportEntryCreated() {
    Report report = ReportFactory.newReport().build();
    reportService.create(report);
  }
}

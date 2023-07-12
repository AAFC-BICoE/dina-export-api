package ca.gc.aafc.dina.export.api.service;

import org.junit.jupiter.api.Test;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.dto.ReportRequestDto;
import ca.gc.aafc.dina.export.api.entity.ReportTemplate;
import ca.gc.aafc.dina.export.api.testsupport.factories.MaterialSampleJsonApiFactory;
import ca.gc.aafc.dina.export.api.testsupport.factories.ReportTemplateFactory;
import ca.gc.aafc.dina.export.api.testsupport.fixtures.ReportRequestTestFixture;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.transaction.Transactional;

public class ReportRequestServiceIT extends BaseIntegrationTest {

  @Inject
  private ReportTemplateService reportService;

  @Inject
  private ReportRequestService reportRequestService;

  @Transactional
  @Test
  public void reportRequestService_onGenerateReport_reportGenerated() throws IOException {
    ReportTemplate reportTemplate = ReportTemplateFactory.newReport()
      .templateFilename("demo.ftlh")
      .includesBarcode(true)
      .build();
    reportService.create(reportTemplate);

    Map<String, Object> matSample = MaterialSampleJsonApiFactory.newMaterialSample();
    String uuid = ((Map<?, ?>) matSample.get("attributes")).get("uuid").toString();

    ReportRequestDto reportRequestDto = ReportRequestTestFixture.newReportRequest()
      .reportTemplateUUID(reportTemplate.getUuid())
      .payload(Map.of("testname", "create_onReportRequest_requestAccepted",
        "elements", List.of(
          Map.of("barcode", Map.of("id", uuid, "content", uuid),
            "data", matSample)
        )))
      .build();
    ReportRequestService.ReportGenerationResult result =
      reportRequestService.generateReport(reportTemplate, reportRequestDto);

    assertNotNull(result.resultIdentifier());
  }

}

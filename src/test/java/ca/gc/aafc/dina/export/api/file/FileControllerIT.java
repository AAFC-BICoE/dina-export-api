package ca.gc.aafc.dina.export.api.file;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.DinaExportModuleApiLauncher;
import ca.gc.aafc.dina.export.api.dto.ReportRequestDto;
import ca.gc.aafc.dina.export.api.entity.ReportTemplate;
import ca.gc.aafc.dina.export.api.service.ReportRequestService;
import ca.gc.aafc.dina.export.api.service.ReportTemplateService;
import ca.gc.aafc.dina.export.api.testsupport.factories.ReportTemplateFactory;
import ca.gc.aafc.dina.export.api.testsupport.fixtures.ReportRequestTestFixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {BaseIntegrationTest.TestConfig.class, DinaExportModuleApiLauncher.class })
@Transactional
public class FileControllerIT extends BaseIntegrationTest {

  @Inject
  private FileController fileController;

  @Inject
  private ReportTemplateService reportService;

  @Inject
  private ReportRequestService reportRequestService;

  @Test
  public void downloadReport_onReportGenerated_reportDownloaded() throws IOException {

    ReportTemplate templateEntity = ReportTemplateFactory.newReport()
      .templateFilename("testHtml.flth")
      .includesBarcode(true)
      .build();
    reportService.create(templateEntity);

    ReportRequestDto request = ReportRequestTestFixture.newReportRequest()
      .reportTemplateUUID(templateEntity.getUuid())
      .payload(Map.of("testname", "create_onReportRequest_requestAccepted",
        "elements", List.of(
          Map.of("barcode", Map.of("id", "xyz", "content", "123")),
          Map.of("barcode", Map.of("id", "qwe", "content", "345"))
        )))
      .build();

    ReportRequestService.ReportGenerationResult result = reportRequestService.generateReport(templateEntity, request);
    ResponseEntity<InputStreamResource> response = fileController.downloadFile(result.resultIdentifier(), FileController.DownloadType.LABEL);
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

}


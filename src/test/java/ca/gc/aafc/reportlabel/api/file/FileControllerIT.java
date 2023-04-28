package ca.gc.aafc.reportlabel.api.file;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import ca.gc.aafc.reportlabel.api.BaseIntegrationTest;
import ca.gc.aafc.reportlabel.api.ReportLabelModuleApiLauncher;
import ca.gc.aafc.reportlabel.api.dto.ReportRequestDto;
import ca.gc.aafc.reportlabel.api.service.ReportRequestService;
import ca.gc.aafc.reportlabel.api.testsupport.fixtures.ReportRequestTestFixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {BaseIntegrationTest.TestConfig.class, ReportLabelModuleApiLauncher.class })
public class FileControllerIT extends BaseIntegrationTest {

  @Inject
  private FileController fileController;

  @Inject
  private ReportRequestService reportRequestService;

  @Test
  public void downloadReport_onReportGenerated_reportDownloaded() throws IOException {
    ReportRequestDto request = ReportRequestTestFixture.newReportRequest()
      .template("testHtml.flth")
      .payload(Map.of("testname", "create_onReportRequest_requestAccepted",
        "elements", List.of(
          Map.of("barcode", Map.of("id", "xyz", "content", "123")),
          Map.of("barcode", Map.of("id", "qwe", "content", "345"))
        )))
      .build();

    ReportRequestService.ReportGenerationResult result = reportRequestService.generateReport(request);
    ResponseEntity<InputStreamResource> response = fileController.downloadReport(result.resultIdentifier());
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

}


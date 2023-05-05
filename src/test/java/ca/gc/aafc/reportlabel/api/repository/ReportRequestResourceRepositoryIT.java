package ca.gc.aafc.reportlabel.api.repository;

import ca.gc.aafc.dina.testsupport.security.WithMockKeycloakUser;
import ca.gc.aafc.reportlabel.api.BaseIntegrationTest;
import ca.gc.aafc.reportlabel.api.ReportLabelModuleApiLauncher;
import ca.gc.aafc.reportlabel.api.dto.ReportDto;
import ca.gc.aafc.reportlabel.api.dto.ReportRequestDto;
import ca.gc.aafc.reportlabel.api.testsupport.fixtures.ReportRequestTestFixture;
import ca.gc.aafc.reportlabel.api.testsupport.fixtures.ReportTestFixture;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;

@SpringBootTest(properties = "keycloak.enabled: true", classes = {BaseIntegrationTest.TestConfig.class, ReportLabelModuleApiLauncher.class })
public class ReportRequestResourceRepositoryIT extends BaseIntegrationTest {

  @Inject
  private ReportRequestRepository transactionRepository;

  @Inject
  private ReportRepository reportRepository;

  @WithMockKeycloakUser(username = "user", groupRole = ReportRequestTestFixture.GROUP + ":USER")
  @Test
  public void create_onReportRequest_requestAccepted() {
    ReportDto templateDto = ReportTestFixture.newReport()
      .templateFilename("testHtml.flth")
      .includesBarcode(true)
      .build();
    templateDto = reportRepository.create(templateDto);

    ReportRequestDto dto = ReportRequestTestFixture.newReportRequest()
      .reportTemplateUUID(templateDto.getUuid())
      .payload(Map.of("testname", "create_onReportRequest_requestAccepted",
        "elements", List.of(
          Map.of("barcode", Map.of("id", "xyz", "content", "123")),
          Map.of("barcode", Map.of("id", "qwe", "content", "345"))
        )))
      .build();
    transactionRepository.create(dto);

    //cleanup
    reportRepository.delete(templateDto.getUuid());
  }

}

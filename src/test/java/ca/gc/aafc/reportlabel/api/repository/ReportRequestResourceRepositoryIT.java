package ca.gc.aafc.reportlabel.api.repository;

import ca.gc.aafc.dina.testsupport.security.WithMockKeycloakUser;
import ca.gc.aafc.reportlabel.api.BaseIntegrationTest;
import ca.gc.aafc.reportlabel.api.ReportLabelModuleApiLauncher;
import ca.gc.aafc.reportlabel.api.dto.ReportRequestDto;
import ca.gc.aafc.reportlabel.api.testsupport.fixtures.ReportRequestTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.inject.Inject;

@SpringBootTest(properties = "keycloak.enabled: true", classes = {BaseIntegrationTest.TestConfig.class, ReportLabelModuleApiLauncher.class })
public class ReportRequestResourceRepositoryIT extends BaseIntegrationTest {

  @Inject
  private ReportRequestRepository transactionRepository;

  @WithMockKeycloakUser(username = "user", groupRole = ReportRequestTestFixture.GROUP + ":USER")
  @Test
  public void create_onReportRequest_requestAccepted() {
    ReportRequestDto dto = ReportRequestTestFixture.newReportRequest().build();
    transactionRepository.create(dto);
  }

}

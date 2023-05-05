package ca.gc.aafc.reportlabel.api.repository;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.dina.testsupport.security.WithMockKeycloakUser;
import ca.gc.aafc.reportlabel.api.BaseIntegrationTest;
import ca.gc.aafc.reportlabel.api.ReportLabelModuleApiLauncher;
import ca.gc.aafc.reportlabel.api.dto.ReportDto;
import ca.gc.aafc.reportlabel.api.testsupport.fixtures.ReportTestFixture;

@SpringBootTest(properties = "keycloak.enabled: true", classes = {BaseIntegrationTest.TestConfig.class, ReportLabelModuleApiLauncher.class })
public class ReportTemplateRepositoryIT extends BaseIntegrationTest {

  @Inject
  private ReportRepository reportRepository;

  @WithMockKeycloakUser(username = "user", groupRole = ReportTestFixture.GROUP + ":USER")
  @Test
  void create_WithAuthUser() {
    ReportDto reportDto = ReportTestFixture.newReport().build();
    reportRepository.create(reportDto);
  }
  
}

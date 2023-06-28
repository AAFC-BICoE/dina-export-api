package ca.gc.aafc.dina.export.api.repository;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.testsupport.security.WithMockKeycloakUser;
import ca.gc.aafc.dina.export.api.DinaExportModuleApiLauncher;
import ca.gc.aafc.dina.export.api.dto.ReportTemplateDto;
import ca.gc.aafc.dina.export.api.testsupport.fixtures.ReportTestFixture;

@SpringBootTest(properties = "keycloak.enabled: true", classes = {BaseIntegrationTest.TestConfig.class, DinaExportModuleApiLauncher.class })
public class ReportTemplateRepositoryIT extends BaseIntegrationTest {

  @Inject
  private ReportTemplateRepository reportRepository;

  @WithMockKeycloakUser(username = "user", groupRole = ReportTestFixture.GROUP + ":USER")
  @Test
  void create_WithAuthUser() {
    ReportTemplateDto reportDto = ReportTestFixture.newReportTemplate().build();
    reportRepository.create(reportDto);
  }
  
}

package ca.gc.aafc.dina.export.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import ca.gc.aafc.dina.exception.ResourceGoneException;
import ca.gc.aafc.dina.exception.ResourceNotFoundException;
import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.DinaExportModuleApiLauncher;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.dto.ReportRequestDto;
import ca.gc.aafc.dina.export.api.dto.ReportTemplateDto;
import ca.gc.aafc.dina.export.api.file.FileController;
import ca.gc.aafc.dina.export.api.generator.FreemarkerReportGeneratorIT;
import ca.gc.aafc.dina.export.api.testsupport.fixtures.ReportRequestTestFixture;
import ca.gc.aafc.dina.export.api.testsupport.fixtures.ReportTemplateTestFixture;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import ca.gc.aafc.dina.jsonapi.JsonApiDocuments;
import ca.gc.aafc.dina.repository.JsonApiModelAssistant;
import ca.gc.aafc.dina.testsupport.jsonapi.JsonAPITestHelper;
import ca.gc.aafc.dina.testsupport.security.WithMockKeycloakUser;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.inject.Inject;

@SpringBootTest(properties = "keycloak.enabled: true", classes = {BaseIntegrationTest.TestConfig.class, DinaExportModuleApiLauncher.class })
public class ReportRequestResourceRepositoryIT extends BaseIntegrationTest {

  @Inject
  private ReportRequestRepository reportRequestRepository;

  @Inject
  private ReportTemplateRepository reportRepository;

  @Inject
  private FileController fileController;

  @WithMockKeycloakUser(username = "user", groupRole = ReportRequestTestFixture.GROUP + ":USER")
  @Test
  public void create_onReportRequest_requestAccepted()
    throws ResourceNotFoundException, ResourceGoneException {
    ReportTemplateDto templateDto = ReportTemplateTestFixture.newReportTemplate()
      .templateFilename("testHtml.flth")
      .includesBarcode(true)
      .build();

    JsonApiDocument reportTemplateToCreate = JsonApiDocuments.createJsonApiDocument(
      null, ReportTemplateDto.TYPENAME,
      JsonAPITestHelper.toAttributeMap(templateDto)
    );
    var created = reportRepository.onCreate(reportTemplateToCreate);
    UUID reportTemplateUuid =  JsonApiModelAssistant.extractUUIDFromRepresentationModelLink(created);

    ReportRequestDto dto = ReportRequestTestFixture.newReportRequest()
      .reportTemplateUUID(reportTemplateUuid)
      .payload(Map.of("testname", "create_onReportRequest_requestAccepted",
        "elements", List.of(
          Map.of("barcode", Map.of("id", "xyz", "content", "123")),
          Map.of("barcode", Map.of("id", "qwe", "content", "345"))
        )))
      .build();
    JsonApiDocument docToCreate = JsonApiDocuments.createJsonApiDocument(
      null, ReportRequestDto.TYPENAME,
      JsonAPITestHelper.toAttributeMap(dto)
    );
    reportRequestRepository.onCreate(docToCreate);

    //cleanup
    reportRepository.onDelete(reportTemplateUuid);
  }

  @WithMockKeycloakUser(username = "user", groupRole = ReportRequestTestFixture.GROUP + ":USER")
  @Test
  public void create_onCSVReportRequest_requestAccepted()
    throws ResourceNotFoundException, ResourceGoneException {

    ReportTemplateDto templateDto = ReportTemplateTestFixture.newReportTemplate()
      .templateFilename("testJson.flt")
      .templateOutputMediaType(MediaType.APPLICATION_JSON_VALUE)
      .outputMediaType(DataExportConfig.TEXT_CSV_VALUE)
      .build();

    JsonApiDocument reportTemplateToCreate = JsonApiDocuments.createJsonApiDocument(
      null, ReportTemplateDto.TYPENAME,
      JsonAPITestHelper.toAttributeMap(templateDto)
    );
    var created = reportRepository.onCreate(reportTemplateToCreate);
    UUID reportTemplateUuid =  JsonApiModelAssistant.extractUUIDFromRepresentationModelLink(created);

    Map<String, Object> payload = Map.of("data", List.of(
          FreemarkerReportGeneratorIT.MyObject.builder()
            .sampleName("ABC-1")
            .extractName("b8")
            .date("2003-04-02").build(),
          FreemarkerReportGeneratorIT.MyObject.builder()
            .sampleName("ABC-2")
            .extractName("b45")
            .date("2003-04-03").build()
        ));

    ReportRequestDto dto = ReportRequestTestFixture.newReportRequest()
      .reportTemplateUUID(reportTemplateUuid)
      .payload(payload)
      .build();

    JsonApiDocument docToCreate = JsonApiDocuments.createJsonApiDocument(
      null, ReportRequestDto.TYPENAME,
      JsonAPITestHelper.toAttributeMap(dto)
    );
    reportRequestRepository.onCreate(docToCreate);

    //cleanup
    reportRepository.onDelete(reportTemplateUuid);
  }

}

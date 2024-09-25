package ca.gc.aafc.dina.export.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.DinaExportModuleApiLauncher;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.dto.ReportRequestDto;
import ca.gc.aafc.dina.export.api.dto.ReportTemplateDto;
import ca.gc.aafc.dina.export.api.file.FileController;
import ca.gc.aafc.dina.export.api.generator.FreemarkerReportGeneratorIT;
import ca.gc.aafc.dina.export.api.testsupport.fixtures.ReportRequestTestFixture;
import ca.gc.aafc.dina.export.api.testsupport.fixtures.ReportTemplateTestFixture;
import ca.gc.aafc.dina.testsupport.jsonapi.JsonAPIRelationship;
import ca.gc.aafc.dina.testsupport.jsonapi.JsonAPITestHelper;
import ca.gc.aafc.dina.testsupport.security.WithMockKeycloakUser;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;

@SpringBootTest(properties = "keycloak.enabled: true", classes = {BaseIntegrationTest.TestConfig.class, DinaExportModuleApiLauncher.class })
public class ReportRequestResourceRepositoryIT extends BaseIntegrationTest {

  @Inject
  private ReportRequestRepository transactionRepository;

  @Inject
  private ReportTemplateRepository reportRepository;

  @Inject
  private FileController fileController;

  @WithMockKeycloakUser(username = "user", groupRole = ReportRequestTestFixture.GROUP + ":USER")
  @Test
  public void create_onReportRequest_requestAccepted() {
    ReportTemplateDto templateDto = ReportTemplateTestFixture.newReportTemplate()
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

  @WithMockKeycloakUser(username = "user", groupRole = ReportRequestTestFixture.GROUP + ":USER")
  @Test
  public void create_onCSVReportRequest_requestAccepted() {

    ReportTemplateDto templateDto = ReportTemplateTestFixture.newReportTemplate()
      .templateFilename("testJson.flt")
      .templateOutputMediaType(MediaType.APPLICATION_JSON_VALUE)
      .outputMediaType(DataExportConfig.TEXT_CSV_VALUE)
      .build();
    templateDto = reportRepository.create(templateDto);

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
      .reportTemplateUUID(templateDto.getUuid())
      .payload(payload)
      .build();
    transactionRepository.create(dto);

    //cleanup
    reportRepository.delete(templateDto.getUuid());
  }


  @WithMockKeycloakUser(username = "user", groupRole = ReportRequestTestFixture.GROUP + ":USER")
  @Test
  public void create_onSequenceSubmissionCSVReportRequest_requestAccepted()
    throws JsonProcessingException {

    ReportTemplateDto templateDto = ReportTemplateTestFixture.newReportTemplate()
      .templateFilename("testSeqSubmissionJson.flt")
      .templateOutputMediaType(MediaType.APPLICATION_JSON_VALUE)
      .outputMediaType(DataExportConfig.TEXT_CSV_VALUE)
      .build();
    templateDto = reportRepository.create(templateDto);

    Map<String, Object> payload = Map.of(
      "seqReaction", List.of(
        JsonAPITestHelper.toJsonAPIMap("seq-reaction", Map.of("", ""),
          JsonAPITestHelper.toRelationshipMap(List.of(
            JsonAPIRelationship.of("seqPrimer", "pcr-primer",
              "8f1b14cd-1cf6-4bff-92c2-732b6d604576"),
            JsonAPIRelationship.of("storageUnitUsage", "storage-unit-usage",
              "0191bd94-c45f-75de-bd5d-47360666708e"),
            JsonAPIRelationship.of("pcrBatchItem", "pcr-batch-item",
              "92513db6-d3b9-4b12-ab2d-c2c3ca57c06a"))
          ),
          "a32f1ae0-d91f-4c7d-ada6-d79e12c5a017")),
      "seq-batch", JsonAPITestHelper.toJsonAPIMap("seq-batch", Map.of("name", "Seq batch1"),
        "a7708ac4-78bd-486d-bb57-1013a8dffbf1"),
      "included", Map.of(
        "pcr-primer", List.of(JsonAPITestHelper.toJsonAPIMap("pcr-primer", Map.of("name", "primer1"), "8f1b14cd-1cf6-4bff-92c2-732b6d604576")),
        "storage-unit-usage", List.of(JsonAPITestHelper.toJsonAPIMap("storage-unit-usage", Map.of("wellColumn", "1", "wellRow", "E", "cellNumber", "5"), "0191bd94-c45f-75de-bd5d-47360666708e")),
        "pcr-batch-item", List.of(JsonAPITestHelper.toJsonAPIMap("pcr-batch-item", Map.of("", ""), JsonAPITestHelper.toRelationshipMap(List.of(
            JsonAPIRelationship.of("materialSample", "material-sample",
              "14e6cd01-d3ca-48b3-9a9f-f1b646c2543"))),
          "92513db6-d3b9-4b12-ab2d-c2c3ca57c06a")),
        "material-sample", List.of(JsonAPITestHelper.toJsonAPIMap("material-sample", Map.of("materialSampleName", "ABC-1"), "14e6cd01-d3ca-48b3-9a9f-f1b646c2543")),
        "region", List.of()
      ));
    ObjectMapper OM = new ObjectMapper();

    System.out.println(OM.writeValueAsString(payload));

    ReportRequestDto dto = ReportRequestTestFixture.newReportRequest()
      .reportTemplateUUID(templateDto.getUuid())
      .payload(payload)
      .build();
    transactionRepository.create(dto);

    //cleanup
    reportRepository.delete(templateDto.getUuid());
  }

}

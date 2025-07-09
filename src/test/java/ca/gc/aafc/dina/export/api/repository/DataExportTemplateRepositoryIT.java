package ca.gc.aafc.dina.export.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.dina.exception.ResourceNotFoundException;
import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.dto.DataExportTemplateDto;
import ca.gc.aafc.dina.export.api.entity.DataExportTemplate;
import ca.gc.aafc.dina.export.api.service.DataExportTemplateService;
import ca.gc.aafc.dina.export.api.testsupport.fixtures.DataExportTemplateTestFixture;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import ca.gc.aafc.dina.jsonapi.JsonApiDocuments;
import ca.gc.aafc.dina.repository.JsonApiModelAssistant;
import ca.gc.aafc.dina.testsupport.jsonapi.JsonAPITestHelper;
import ca.gc.aafc.dina.testsupport.security.WithMockKeycloakUser;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.ValidationException;

@SpringBootTest(properties = "keycloak.enabled: true")
@Transactional
public class DataExportTemplateRepositoryIT extends BaseIntegrationTest {

  @Inject
  private DataExportTemplateRepository dataExportTemplateRepository;

  @Inject
  private DataExportTemplateService dataExportTemplateService;

  @Test
  @WithMockKeycloakUser(username = "user", groupRole = DataExportTemplateTestFixture.GROUP + ":user")
  public void createDataExportTemplate_onSuccess_createDataExportTemplatePersisted() throws ResourceNotFoundException {

    DataExportTemplateDto deTemplate = DataExportTemplateTestFixture.newDataExportTemplate().build();

    JsonApiDocument docToCreate = JsonApiDocuments.createJsonApiDocument(
      null, DataExportTemplateDto.TYPENAME,
      JsonAPITestHelper.toAttributeMap(deTemplate)
    );

    var created = dataExportTemplateRepository.onCreate(docToCreate);
    UUID uuid = JsonApiModelAssistant.extractUUIDFromRepresentationModelLink(created);

    DataExportTemplate result = dataExportTemplateService.findOne(uuid, DataExportTemplate.class);
    assertNotNull(result.getCreatedBy());
    assertEquals(DataExportTemplateTestFixture.GROUP, result.getGroup());
  }

  @Test
  @WithMockKeycloakUser(username = "user", groupRole = DataExportTemplateTestFixture.GROUP + ":user")
  public void createDataExportTemplate_setBothRestrictions_throwsValidationException() throws ResourceNotFoundException {

    DataExportTemplateDto deTemplate = DataExportTemplateTestFixture.newDataExportTemplate()
        .restrictToCreatedBy(true)
        .publiclyReleasable(true)
        .build();

    JsonApiDocument docToCreate = JsonApiDocuments.createJsonApiDocument(
      null, DataExportTemplateDto.TYPENAME,
      JsonAPITestHelper.toAttributeMap(deTemplate)
    );

    ValidationException exception = assertThrows(
      ValidationException.class, 
      () -> dataExportTemplateRepository.onCreate(docToCreate));
    assertEquals("DataExportTemplate can be publiclyReleasable or restricted to createdBy but not both", exception.getMessage());
  }
}

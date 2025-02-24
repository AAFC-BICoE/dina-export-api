package ca.gc.aafc.dina.export.api.repository;

import java.util.UUID;
import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.IanaLinkRelations;

import ca.gc.aafc.dina.exception.ResourceNotFoundException;
import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.dto.DataExportTemplateDto;
import ca.gc.aafc.dina.export.api.testsupport.fixtures.DataExportTemplateTestFixture;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import ca.gc.aafc.dina.jsonapi.JsonApiDocuments;
import ca.gc.aafc.dina.testsupport.jsonapi.JsonAPITestHelper;

public class DataExportTemplateRepositoryIT extends BaseIntegrationTest {

  @Inject
  private DataExportTemplateRepository dataExportTemplateRepository;

  @Test
  public void create() throws ResourceNotFoundException {

    DataExportTemplateDto deTemplate = DataExportTemplateTestFixture.newDataExportTemplate();

    JsonApiDocument docToCreate = JsonApiDocuments.createJsonApiDocument(
      null, DataExportTemplateDto.TYPENAME,
      JsonAPITestHelper.toAttributeMap(deTemplate)
    );

    var created = dataExportTemplateRepository.handleCreate(docToCreate);

    UUID uuid = UUID.fromString(StringUtils.substringAfterLast(created.getBody().getLink(
      IanaLinkRelations.SELF).get().getHref(), "/"));

  //  Person result = personService.findOne(uuid, Person.class, Set.of("organizations"));
  }
}

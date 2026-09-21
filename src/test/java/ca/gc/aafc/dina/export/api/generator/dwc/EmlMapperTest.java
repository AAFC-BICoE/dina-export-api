package ca.gc.aafc.dina.export.api.generator.dwc;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ca.aafc.eml.generated.eml.AgentType;
import ca.aafc.eml.generated.eml.AgentWithRoleType;
import ca.aafc.eml.generated.eml.AwardType;
import ca.aafc.eml.generated.eml.Coverage;
import ca.aafc.eml.generated.eml.Dataset;
import ca.aafc.eml.generated.eml.Description;
import ca.aafc.eml.generated.eml.Eml;
import ca.aafc.eml.generated.eml.GeographicCoverage;
import ca.aafc.eml.generated.eml.IndividualName;
import ca.aafc.eml.generated.eml.Methods;
import ca.aafc.eml.generated.eml.Para;
import ca.aafc.eml.generated.eml.ProjectType;
import ca.aafc.eml.generated.eml.TaxonomicCoverage;
import ca.aafc.eml.generated.eml.TemporalCoverage;
import ca.aafc.eml.generated.eml.Ulink;
import ca.gc.aafc.dina.dto.BaseDatasetDto;
import ca.gc.aafc.dina.entity.AgentRoles;
import ca.gc.aafc.dina.export.api.service.DinaApiClient;
import ca.gc.aafc.dina.i18n.MultilingualDescription;
import ca.gc.aafc.dina.i18n.MultilingualTitle;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import okhttp3.HttpUrl;
import jakarta.xml.bind.JAXBElement;

public class EmlMapperTest {

  @Test
  public void datasetToEml_mapsAllFields() {
    BaseDatasetDto dataset = new BaseDatasetDto();
    dataset.setUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

    UUID principalInvestigator = UUID.fromString("11111111-2222-3333-4444-555555555555");
    JsonApiDocument personnelDoc = JsonApiDocument.builder()
        .data(JsonApiDocument.ResourceObject.builder()
            .type("person")
            .id(principalInvestigator)
            .attributes(Map.of(
                "displayName", "John Smith",
                "givenNames", "John",
                "familyNames", "Smith"))
            .build())
        .build();

    MultilingualTitle title = new MultilingualTitle();
    title.setTitles(List.of(
        MultilingualTitle.MultilingualTitlePair.of("en", "Ants of Canada"),
        MultilingualTitle.MultilingualTitlePair.of("fr", "Fourmis du Canada")));
    dataset.setMultilingualTitle(title);

    MultilingualDescription description = new MultilingualDescription();
    description.setDescriptions(List.of(
        MultilingualDescription.MultilingualPair.of("en", "A collection of ant specimens.")));
    dataset.setMultilingualDescription(description);

    dataset.setKeywordSets(List.of(
        new BaseDatasetDto.KeywordSet(List.of("ants", "Formicidae"), "GBIF"),
        new BaseDatasetDto.KeywordSet(List.of("Canada"), null)));
    dataset.setUsageRights(new BaseDatasetDto.UsageRights(
        "CC-BY", "https://creativecommons.org/licenses/by/4.0/", "Free to use with attribution."));
    dataset.setDatasetVersion("1.2");
    dataset.setPublicationDate(LocalDate.of(2025, 5, 1));
    dataset.setCoverage(new BaseDatasetDto.Coverage(
        new BaseDatasetDto.GeographicCoverage("Canada",
            new BaseDatasetDto.BoundingBox(-140.0, 41.0, -52.0, 70.0)),
        new BaseDatasetDto.TemporalCoverage(
            LocalDate.of(2000, 1, 1), LocalDate.of(2020, 12, 31)),
        List.of(new BaseDatasetDto.TaxonomicCoverage("kingdom", "Animalia", "Animals"))));
    dataset.setMethods(new BaseDatasetDto.Methods(
        List.of("Collect specimens", "Identify specimens"),
        new BaseDatasetDto.Sampling("Canada 2000-2020", "Pitfall traps"),
        List.of("Taxonomic verification")));
    dataset.setProject(new BaseDatasetDto.Project(
        "Project title",
        "Project abstract",
        "Funding agency",
        List.of(AgentRoles.builder().agent(principalInvestigator)
            .roles(List.of("principalInvestigator")).build()),
        List.of(new BaseDatasetDto.Award(
            "NSERC", List.of("10.13039/501100000038"), "12345", "Award title",
            "https://award.example.com")),
        "Study area",
        "Design description"));

    DinaApiClient client = mock(DinaApiClient.class);
    when(client.fetchDocument(any(HttpUrl.class))).thenReturn(personnelDoc);

    EmlMapper emlMapper = new EmlMapper(client, "http://localhost:8082/api/v1");
    Eml eml = emlMapper.datasetToEml(dataset, "dwca.zip");
    assertEquals("123e4567-e89b-12d3-a456-426614174000/v1.2", eml.getPackageId());

    Dataset emlDataset = eml.getDataset();
    assertNotNull(emlDataset);

    // Identifier
    assertEquals(List.of("dwca.zip"), emlDataset.getAlternateIdentifier());
    assertEquals("2025-05-01", emlDataset.getPubDate());
    
    // Titles
    assertEquals(2, emlDataset.getTitle().size());
    assertEquals("en", emlDataset.getTitle().get(0).getLang());
    assertEquals("Ants of Canada", emlDataset.getTitle().get(0).getValue());
    assertEquals("fr", emlDataset.getTitle().get(1).getLang());
    assertEquals("Fourmis du Canada", emlDataset.getTitle().get(1).getValue());

    // Abstract
    assertNotNull(emlDataset.getAbstract());
    assertEquals("en", emlDataset.getAbstract().getLang());
    assertEquals(List.of("A collection of ant specimens."), emlDataset.getAbstract().getContent());

    // Keyword sets
    assertEquals(2, emlDataset.getKeywordSet().size());
    assertEquals(List.of("ants", "Formicidae"), emlDataset.getKeywordSet().get(0).getKeyword());
    assertEquals("GBIF", emlDataset.getKeywordSet().get(0).getKeywordThesaurus());
    assertEquals(List.of("Canada"), emlDataset.getKeywordSet().get(1).getKeyword());
    assertNull(emlDataset.getKeywordSet().get(1).getKeywordThesaurus());

    // License and rights
    assertNotNull(emlDataset.getLicensed());
    assertEquals("CC-BY", emlDataset.getLicensed().getLicenseName());
    assertEquals("https://creativecommons.org/licenses/by/4.0/", emlDataset.getLicensed().getUrl());

    assertNotNull(emlDataset.getIntellectualRights());

    Para rightsPara = emlDataset.getIntellectualRights().getPara();
    assertEquals(List.of("This work is licensed under a ", " ."),
        rightsPara.getContent().stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .toList());

    Ulink ulink = (Ulink) rightsPara.getContent().stream()
        .filter(Ulink.class::isInstance)
        .findFirst()
        .orElseThrow();
    assertEquals("https://creativecommons.org/licenses/by/4.0/", ulink.getUrl());
    assertEquals("CC-BY",
        ((JAXBElement<String>) ulink.getContent().get(0)).getValue());

    // Coverage: geographic, temporal, taxonomic (in that order)
    Coverage coverage = emlDataset.getCoverage();
    assertNotNull(coverage);
    List<Object> coverageParts = coverage.getGeographicCoverageOrTemporalCoverageOrTaxonomicCoverage();
    assertEquals(3, coverageParts.size());

    GeographicCoverage geographicCoverage = (GeographicCoverage) coverageParts.get(0);
    assertEquals("Canada", geographicCoverage.getGeographicDescription());
    assertNotNull(geographicCoverage.getBoundingCoordinates());
    assertEquals(0, geographicCoverage.getBoundingCoordinates().getWestBoundingCoordinate()
        .compareTo(BigDecimal.valueOf(-140.0)));
    assertEquals(0, geographicCoverage.getBoundingCoordinates().getSouthBoundingCoordinate()
        .compareTo(BigDecimal.valueOf(41.0)));
    assertEquals(0, geographicCoverage.getBoundingCoordinates().getEastBoundingCoordinate()
        .compareTo(BigDecimal.valueOf(-52.0)));
    assertEquals(0, geographicCoverage.getBoundingCoordinates().getNorthBoundingCoordinate()
        .compareTo(BigDecimal.valueOf(70.0)));

    TemporalCoverage temporalCoverage = (TemporalCoverage) coverageParts.get(1);
    assertNotNull(temporalCoverage.getRangeOfDates());
    assertEquals("2000-01-01", temporalCoverage.getRangeOfDates().getBeginDate().getCalendarDate());
    assertEquals("2020-12-31", temporalCoverage.getRangeOfDates().getEndDate().getCalendarDate());

    TaxonomicCoverage taxonomicCoverage = (TaxonomicCoverage) coverageParts.get(2);
    assertEquals(1, taxonomicCoverage.getTaxonomicClassification().size());
    assertEquals("kingdom", taxonomicCoverage.getTaxonomicClassification().get(0).getTaxonRankName());
    assertEquals("Animalia", taxonomicCoverage.getTaxonomicClassification().get(0).getTaxonRankValue());
    assertEquals("Animals", taxonomicCoverage.getTaxonomicClassification().get(0).getCommonName());

    // Methods
    Methods methods = emlDataset.getMethods();
    assertNotNull(methods);
    List<JAXBElement<?>> methodParts = methods.getMethodStepAndSamplingAndQualityControl();
    assertEquals(4, methodParts.size());
    assertEquals("methodStep", methodParts.get(0).getName().getLocalPart());
    assertEquals("Collect specimens", descriptionText((Description) methodParts.get(0).getValue()));
    assertEquals("methodStep", methodParts.get(1).getName().getLocalPart());
    assertEquals("Identify specimens", descriptionText((Description) methodParts.get(1).getValue()));
    assertEquals("sampling", methodParts.get(2).getName().getLocalPart());
    Methods.Sampling sampling = (Methods.Sampling) methodParts.get(2).getValue();
    assertEquals("Canada 2000-2020", descriptionText(sampling.getStudyExtent()));
    assertEquals("Pitfall traps", sampling.getSamplingDescription().getPara().getContent().get(0));
    assertEquals("qualityControl", methodParts.get(3).getName().getLocalPart());
    assertEquals("Taxonomic verification", descriptionText((Description) methodParts.get(3).getValue()));

    // Project
    ProjectType project = emlDataset.getProject();
    assertNotNull(project);
    assertEquals("Project title", project.getTitle().getValue());
    assertEquals(List.of("Project abstract"), project.getAbstract().getContent());
    assertEquals("Funding agency", project.getFunding().getPara().getContent().get(0));
    assertEquals("Design description", descriptionText(project.getDesignDescription()));

    assertEquals(1, project.getAward().size());
    AwardType award = project.getAward().get(0);
    assertEquals("NSERC", award.getFunderName());
    assertEquals(List.of("10.13039/501100000038"), award.getFunderIdentifier());
    assertEquals("12345", award.getAwardNumber());
    assertEquals("Award title", award.getTitle());
    assertEquals("https://award.example.com", award.getAwardUrl());

    assertEquals(1, project.getPersonnel().size());
    AgentWithRoleType projectPersonnel = project.getPersonnel().get(0);
    assertEquals("principalInvestigator", projectPersonnel.getRole());
    assertTrue(projectPersonnel.getId().isEmpty());
    IndividualName personnelName = (IndividualName) projectPersonnel
        .getOrganizationNameOrIndividualNameOrPositionName().get(0);
    assertEquals("John", personnelName.getGivenName());
    assertEquals("Smith", personnelName.getSurName());
  }

  @Test
  public void datasetToEml_onEmptyDataset_returnsEmptyDataset() {
    EmlMapper emlMapper = new EmlMapper(mock(DinaApiClient.class), "http://localhost:8082/api/v1");
    BaseDatasetDto dataset = new BaseDatasetDto();
    dataset.setUuid(UUID.randomUUID());
    Dataset emlDataset = emlMapper.datasetToEml(dataset, "dwca.zip").getDataset();

    assertNotNull(emlDataset);
    assertEquals(List.of("dwca.zip"), emlDataset.getAlternateIdentifier());
    assertTrue(emlDataset.getTitle().isEmpty());
    assertNull(emlDataset.getAbstract());
    assertTrue(emlDataset.getKeywordSet().isEmpty());
    assertNull(emlDataset.getLicensed());
    assertNull(emlDataset.getIntellectualRights());
    assertNull(emlDataset.getCoverage());
  }

  @Test
  public void datasetToEml_mapsAgentsToCreatorMetadataProviderContactPublisherAndAssociatedParty() {
    UUID creatorUUID = UUID.randomUUID();
    UUID metadataProviderUUID = UUID.randomUUID();
    UUID contactUUID = UUID.randomUUID();
    UUID publisherUUID = UUID.randomUUID();
    UUID associatedPartyUUID = UUID.randomUUID();

    JsonApiDocument creatorDoc = JsonApiDocument.builder()
        .data(JsonApiDocument.ResourceObject.builder()
            .type("person")
            .id(creatorUUID)
            .attributes(Map.of(
                "displayName", "Jane Doe",
                "givenNames", "Jane",
                "familyNames", "Doe",
                "email", "jane@example.com",
                "webpage", "https://jane.example.com"))
            .build())
        .build();

    JsonApiDocument metadataProviderDoc = JsonApiDocument.builder()
        .data(JsonApiDocument.ResourceObject.builder()
            .type("organization")
            .id(metadataProviderUUID)
            .attributes(Map.of("names", List.of(
                Map.of("languageCode", "EN", "name", "Example Org"))))
            .build())
        .build();

    JsonApiDocument contactDoc = JsonApiDocument.builder()
        .data(JsonApiDocument.ResourceObject.builder()
            .type("person")
            .id(contactUUID)
            .attributes(Map.of(
                "displayName", "Contact Person",
                "givenNames", "Contact",
                "familyNames", "Person"))
            .build())
        .build();

    JsonApiDocument publisherDoc = JsonApiDocument.builder()
        .data(JsonApiDocument.ResourceObject.builder()
            .type("organization")
            .id(publisherUUID)
            .attributes(Map.of("names", List.of(
                Map.of("languageCode", "EN", "name", "Publisher Org"))))
            .build())
        .build();

    JsonApiDocument associatedPartyDoc = JsonApiDocument.builder()
        .data(JsonApiDocument.ResourceObject.builder()
            .type("person")
            .id(associatedPartyUUID)
            .attributes(Map.of(
                "displayName", "Associate Person",
                "givenNames", "Associate",
                "familyNames", "Person"))
            .build())
        .build();

    DinaApiClient client = mock(DinaApiClient.class);
    when(client.fetchDocument(any(HttpUrl.class))).thenAnswer(invocation -> {
      String path = invocation.getArgument(0, HttpUrl.class).encodedPath();
      if (path.endsWith("/person/" + creatorUUID)) {
        return creatorDoc;
      }
      if (path.endsWith("/organization/" + metadataProviderUUID)) {
        return metadataProviderDoc;
      }
      if (path.endsWith("/person/" + contactUUID)) {
        return contactDoc;
      }
      if (path.endsWith("/organization/" + publisherUUID)) {
        return publisherDoc;
      }
      if (path.endsWith("/person/" + associatedPartyUUID)) {
        return associatedPartyDoc;
      }
      return null;
    });

    EmlMapper emlMapper = new EmlMapper(client, "http://localhost:8082/api/v1");

    BaseDatasetDto dataset = new BaseDatasetDto();
    dataset.setUuid(UUID.randomUUID());
    dataset.setAgentRoles(List.of(
        AgentRoles.builder().agent(creatorUUID).roles(List.of(BaseDatasetDto.AGENT_ROLE_CREATOR)).build(),
        AgentRoles.builder().agent(metadataProviderUUID).roles(List.of(BaseDatasetDto.AGENT_ROLE_METADATA_PROVIDER)).build(),
        AgentRoles.builder().agent(contactUUID).roles(List.of(BaseDatasetDto.AGENT_ROLE_CONTACT)).build(),
        AgentRoles.builder().agent(publisherUUID).roles(List.of(BaseDatasetDto.AGENT_ROLE_PUBLISHER)).build(),
        AgentRoles.builder().agent(associatedPartyUUID).roles(List.of(BaseDatasetDto.AGENT_ROLE_ASSOCIATED_PARTY)).build(),
        AgentRoles.builder().agent(UUID.randomUUID()).roles(List.of("helper", "manager")).build()));

    Dataset emlDataset = emlMapper.datasetToEml(dataset, "dwca.zip").getDataset();

    assertEquals(1, emlDataset.getCreator().size());
    assertEquals(1, emlDataset.getMetadataProvider().size());

    // Creator resolved as a person
    AgentType creatorAgent = emlDataset.getCreator().get(0);
    assertTrue(creatorAgent.getId().isEmpty());
    assertEquals(List.of("jane@example.com"), creatorAgent.getElectronicMailAddress());
    assertEquals(List.of("https://jane.example.com"), creatorAgent.getOnlineUrl());
    IndividualName individualName = (IndividualName) creatorAgent
        .getOrganizationNameOrIndividualNameOrPositionName().get(0);
    assertEquals("Jane", individualName.getGivenName());
    assertEquals("Doe", individualName.getSurName());

    // Metadata provider resolved as an organization
    AgentType metadataProviderAgent = emlDataset.getMetadataProvider().get(0);
    assertTrue(metadataProviderAgent.getId().isEmpty());
    assertEquals("Example Org", ((JAXBElement<?>) metadataProviderAgent
        .getOrganizationNameOrIndividualNameOrPositionName().get(0)).getValue());

    // Explicit contact is preferred over the creator fallback
    assertEquals(1, emlDataset.getContact().size());
    assertTrue(emlDataset.getContact().get(0).getId().isEmpty());

    // Publisher resolved as an organization
    assertNotNull(emlDataset.getPublisher());
    assertTrue(emlDataset.getPublisher().getId().isEmpty());

    // Associated party resolved as a person with its role
    assertEquals(1, emlDataset.getAssociatedParty().size());
    AgentWithRoleType associatedPartyAgent = emlDataset.getAssociatedParty().get(0);
    assertTrue(associatedPartyAgent.getId().isEmpty());
    assertEquals(BaseDatasetDto.AGENT_ROLE_ASSOCIATED_PARTY, associatedPartyAgent.getRole());
  }

  private static String descriptionText(Description description) {
    return ((Para) description.getDescription().getContent().get(0)).getContent().get(0).toString();
  }

}

package ca.gc.aafc.dina.export.api.generator.dwc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ca.aafc.eml.generated.eml.AgentType;
import ca.aafc.eml.generated.eml.AgentWithRoleType;
import ca.aafc.eml.generated.eml.AwardType;
import ca.aafc.eml.generated.eml.CalendarDate;
import ca.aafc.eml.generated.eml.Coverage;
import ca.aafc.eml.generated.eml.Dataset;
import ca.aafc.eml.generated.eml.Description;
import ca.aafc.eml.generated.eml.Description2;
import ca.aafc.eml.generated.eml.Eml;
import ca.aafc.eml.generated.eml.GeographicCoverage;
import ca.aafc.eml.generated.eml.I18NString;
import ca.aafc.eml.generated.eml.IndividualName;
import ca.aafc.eml.generated.eml.IntellectualRights;
import ca.aafc.eml.generated.eml.KeywordSet;
import ca.aafc.eml.generated.eml.Licensed;
import ca.aafc.eml.generated.eml.Methods;
import ca.aafc.eml.generated.eml.ObjectFactory;
import ca.aafc.eml.generated.eml.Para;
import ca.aafc.eml.generated.eml.ProjectType;
import ca.aafc.eml.generated.eml.TaxonomicCoverage;
import ca.aafc.eml.generated.eml.TemporalCoverage;
import ca.aafc.eml.generated.eml.TextType;
import ca.aafc.eml.generated.eml.Ulink;
import jakarta.xml.bind.JAXBElement;
import ca.gc.aafc.dina.dto.BaseDatasetDto;
import ca.gc.aafc.dina.entity.AgentRoles;
import ca.gc.aafc.dina.export.api.service.DinaApiClient;
import ca.gc.aafc.dina.i18n.MultilingualDescription;
import ca.gc.aafc.dina.i18n.MultilingualTitle;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import okhttp3.HttpUrl;

/**
 * Responsible to map Dina {@link BaseDatasetDto} to Eml dataset
 */
@Component
public final class EmlMapper {

  private final DinaApiClient dinaApiClient;
  private final String agentApiUrl;

  private static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();
  private static final String EML_SYSTEM = "https://www.dina-project.net";

  public EmlMapper(DinaApiClient dinaApiClient,
                   @Value("${dina.export.agent.apiUrl}") String agentApiUrl) {
    this.dinaApiClient = dinaApiClient;
    this.agentApiUrl = agentApiUrl;
  }

  /**
   * Maps a DINA {@link BaseDatasetDto} resource into a schema derived EML
   * {@link Dataset}.
   *
   * @param dataset the DINA dataset to map
   * @param exportFilename the export filename to use as the alternate identifier
   * @return an EML document wrapping the mapped dataset
   */
  public Eml datasetToEml(BaseDatasetDto dataset, String exportFilename) {
    Eml eml = new Eml();

    String packageId = dataset.getUuid().toString();
    if (StringUtils.isNotBlank(dataset.getDatasetVersion())) {
      packageId += "/" + StringUtils.prependIfMissing(dataset.getDatasetVersion(), "v");
    }
    eml.setPackageId(packageId);
    eml.getSystem().add(EML_SYSTEM);

    Dataset emlDataset = new Dataset();

    if (StringUtils.isNotBlank(exportFilename)) {
      emlDataset.getAlternateIdentifier().add(exportFilename);
    }

    if (dataset.getPublicationDate() != null) {
      emlDataset.setPubDate(dataset.getPublicationDate().toString());
    }
    
    mapTitles(dataset, emlDataset);
    mapAbstract(dataset, emlDataset);
    mapKeywordSets(dataset, emlDataset);
    mapRights(dataset, emlDataset);
    mapAgents(dataset, emlDataset);
    emlDataset.setCoverage(buildCoverage(dataset.getCoverage()));
    emlDataset.setMethods(buildMethods(dataset.getMethods()));
    emlDataset.setProject(buildProject(dataset.getProject()));

    eml.setDataset(emlDataset);
    return eml;
  }

  private static void mapTitles(BaseDatasetDto dataset, Dataset emlDataset) {
    MultilingualTitle multilingualTitle = dataset.getMultilingualTitle();
    if (multilingualTitle == null || multilingualTitle.getTitles() == null) {
      return;
    }

    for (MultilingualTitle.MultilingualTitlePair pair : multilingualTitle.getTitles()) {
      I18NString title = new I18NString();
      title.setLang(pair.getLang());
      title.setValue(pair.getTitle());
      emlDataset.getTitle().add(title);
    }
  }

  private static void mapAbstract(BaseDatasetDto dataset, Dataset emlDataset) {
    MultilingualDescription multilingualDescription = dataset.getMultilingualDescription();
    if (multilingualDescription == null || multilingualDescription.getDescriptions() == null
        || multilingualDescription.getDescriptions().isEmpty()) {
      return;
    }

    // EML supports a single abstract, so the first description is used.
    MultilingualDescription.MultilingualPair description = multilingualDescription.getDescriptions().getFirst();
    TextType abstractText = new TextType();
    abstractText.setLang(description.getLang());
    abstractText.getContent().add(description.getDesc());
    emlDataset.setAbstract(abstractText);
  }

  private static void mapKeywordSets(BaseDatasetDto dataset, Dataset emlDataset) {
    if (dataset.getKeywordSets() == null) {
      return;
    }

    for (BaseDatasetDto.KeywordSet keywordSet : dataset.getKeywordSets()) {
      KeywordSet emlKeywordSet = new KeywordSet();
      if (keywordSet.keywords() != null) {
        emlKeywordSet.getKeyword().addAll(keywordSet.keywords());
      }
      // TODO: Uncomment if we have a useful case for this
      //emlKeywordSet.setKeywordThesaurus(keywordSet.thesaurus());
      emlDataset.getKeywordSet().add(emlKeywordSet);
    }
  }

  private static void mapRights(BaseDatasetDto dataset, Dataset emlDataset) {
    BaseDatasetDto.UsageRights usageRights = dataset.getUsageRights();
    if (usageRights == null) {
      return;
    }

    Licensed licensed = new Licensed();
    licensed.setLicenseName(usageRights.licenseName());
    licensed.setUrl(usageRights.licenseUrl());
    emlDataset.setLicensed(licensed);

    IntellectualRights intellectualRights = new IntellectualRights();
    Para para = new Para();
    para.getContent().add("This work is licensed under a ");

    Ulink ulink = new Ulink();
    ulink.setUrl(usageRights.licenseUrl());
    ulink.getContent().add(OBJECT_FACTORY.createUlinkCitetitle(usageRights.licenseName()));
    para.getContent().add(ulink);
    para.getContent().add(" .");

    intellectualRights.setPara(para);
    emlDataset.setIntellectualRights(intellectualRights);
  }

  private void mapAgents(BaseDatasetDto dataset, Dataset emlDataset) {
    if (dataset.getAgentRoles() == null) {
      return;
    }

    Optional<AgentType> creator = dataset.getAgentRoles().stream()
        .filter(agentRole -> hasRole(agentRole, BaseDatasetDto.AGENT_ROLE_CREATOR))
        .map(this::toAgentType)
        .filter(Objects::nonNull)
        .findFirst();
    Optional<AgentType> metadataProvider = dataset.getAgentRoles().stream()
        .filter(agentRole -> hasRole(agentRole, BaseDatasetDto.AGENT_ROLE_METADATA_PROVIDER))
        .map(this::toAgentType)
        .filter(Objects::nonNull)
        .findFirst();
    Optional<AgentType> contact = dataset.getAgentRoles().stream()
        .filter(agentRole -> hasRole(agentRole, BaseDatasetDto.AGENT_ROLE_CONTACT))
        .map(this::toAgentType)
        .filter(Objects::nonNull)
        .findFirst();
    Optional<AgentType> publisher = dataset.getAgentRoles().stream()
        .filter(agentRole -> hasRole(agentRole, BaseDatasetDto.AGENT_ROLE_PUBLISHER))
        .map(this::toAgentType)
        .filter(Objects::nonNull)
        .findFirst();
    Optional<AgentWithRoleType> associatedParty = dataset.getAgentRoles().stream()
        .filter(agentRole -> hasRole(agentRole, BaseDatasetDto.AGENT_ROLE_ASSOCIATED_PARTY))
        .map(this::toAgentWithRoleType)
        .filter(Objects::nonNull)
        .findFirst();

    creator.ifPresent(emlDataset.getCreator()::add);
    metadataProvider.ifPresent(emlDataset.getMetadataProvider()::add);
    publisher.ifPresent(emlDataset::setPublisher);
    associatedParty.ifPresent(emlDataset.getAssociatedParty()::add);

    // EML requires a contact; prefer an explicit contact, then the creator, then the metadataProvider.
    contact.or(() -> creator).or(() -> metadataProvider)
        .ifPresent(emlDataset.getContact()::add);
  }

  private static boolean hasRole(AgentRoles agentRoles, String role) {
    return agentRoles.getRoles() != null && agentRoles.getRoles().contains(role);
  }

  private AgentType toAgentType(AgentRoles agentRoles) {
    AgentType agentType = new AgentType();
    populateAgentType(agentType, agentRoles);
    return agentType.getOrganizationNameOrIndividualNameOrPositionName().isEmpty() ? null : agentType;
  }

  private void populateAgentType(AgentType agentType, AgentRoles agentRoles) {
    UUID agentUuid = agentRoles.getAgent();

    JsonApiDocument agentDoc = resolveAgent(agentUuid);
    if (agentDoc == null || agentDoc.getAttributes() == null) {
      return;
    }

    Map<String, Object> attributes = agentDoc.getAttributes();

    // Only map persons. An organization should only appear as a person's
    // affiliation, never as a standalone agent.
    if ("organization".equals(agentDoc.getType())) {
      return;
    }

    String displayName = text(attributes.get("displayName"));
    String givenName = text(attributes.get("givenNames"));
    String familyName = text(attributes.get("familyNames"));
    // EML individualName requires surName; fall back to displayName when familyNames is missing.
    String surName = familyName != null ? familyName : displayName;
    if (givenName != null || surName != null) {
      IndividualName individualName = new IndividualName();
      individualName.setGivenName(givenName);
      individualName.setSurName(surName);
      agentType.getOrganizationNameOrIndividualNameOrPositionName().add(individualName);
    }

    // Always add the names of the organizations the person is linked to.
    for (JsonApiDocument organizationDoc : resolveOrganizationsForPerson(agentDoc)) {
      if (organizationDoc.getAttributes() == null) {
        continue;
      }
      String organizationName = organizationName(organizationDoc.getAttributes());
      if (organizationName != null) {
        agentType.getOrganizationNameOrIndividualNameOrPositionName().add(
          OBJECT_FACTORY.createAgentTypeOrganizationName(organizationName));
      }
    }

    String email = text(attributes.get("email"));
    if (email != null) {
      agentType.getElectronicMailAddress().add(email);
    }

    String webpage = text(attributes.get("webpage"));
    if (webpage != null) {
      agentType.getOnlineUrl().add(webpage);
    }
  }

  /**
   * Resolves the organizations a person document is linked to through its {@code organizations}
   * relationship. Returns an empty list when none is linked.
   */
  private List<JsonApiDocument> resolveOrganizationsForPerson(JsonApiDocument personDoc) {
    List<JsonApiDocument> organizations = new ArrayList<>();
    for (UUID organizationUuid : organizationUuids(personDoc)) {
      JsonApiDocument organizationDoc =
        fetchAgentDocument(agentApiUrl + "/organization/" + organizationUuid);
      if (organizationDoc != null) {
        organizations.add(organizationDoc);
      }
    }
    return organizations;
  }

  /**
   * Extracts the name of an organization from its attributes, preferring the English name and
   * falling back to the first available name. Supports the legacy {@code displayName} attribute.
   */
  private static String organizationName(Map<String, Object> attributes) {
    if (attributes == null) {
      return null;
    }

    Object namesValue = attributes.get("names");
    if (namesValue instanceof Collection<?> names && !names.isEmpty()) {
      for (Object name : names) {
        if (name instanceof Map<?, ?> nameMap
            && "EN".equalsIgnoreCase(text(nameMap.get("languageCode")))) {
          String englishName = text(nameMap.get("name"));
          if (englishName != null) {
            return englishName;
          }
        }
      }
      Object first = names.iterator().next();
      if (first instanceof Map<?, ?> nameMap) {
        String fallbackName = text(nameMap.get("name"));
        if (fallbackName != null) {
          return fallbackName;
        }
      }
    }

    return text(attributes.get("displayName"));
  }

  /**
   * Returns the organization UUIDs referenced by a person's {@code organizations} relationship,
   * preserving order and removing duplicates.
   */
  private static List<UUID> organizationUuids(JsonApiDocument personDoc) {
    Map<String, JsonApiDocument.RelationshipObject> relationships = personDoc.getRelationships();
    if (relationships == null) {
      return List.of();
    }

    JsonApiDocument.RelationshipObject organizations = relationships.get("organizations");
    if (organizations == null || organizations.isNull()) {
      return List.of();
    }

    Set<UUID> uuids = new LinkedHashSet<>();
    Object data = organizations.getData();
    if (data instanceof Collection<?> collection) {
      for (Object item : collection) {
        if (item instanceof Map<?, ?> resourceIdentifier) {
          parseUuid(resourceIdentifier.get("id")).ifPresent(uuids::add);
        }
      }
    } else if (data instanceof Map<?, ?> resourceIdentifier) {
      parseUuid(resourceIdentifier.get("id")).ifPresent(uuids::add);
    }
    return new ArrayList<>(uuids);
  }

  private static Optional<UUID> parseUuid(Object value) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(value.toString()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }

  private JsonApiDocument resolveAgent(UUID agentUuid) {
    if (agentUuid == null) {
      return null;
    }
    JsonApiDocument person = fetchAgentDocument(agentApiUrl + "/person/" + agentUuid + "?include=organizations");
    return person != null ? person : fetchAgentDocument(agentApiUrl + "/organization/" + agentUuid);
  }

  private JsonApiDocument fetchAgentDocument(String url) {
    HttpUrl httpUrl = HttpUrl.parse(url);
    return httpUrl == null ? null : dinaApiClient.fetchDocument(httpUrl);
  }

  private static Methods buildMethods(BaseDatasetDto.Methods methods) {
    if (methods == null) {
      return null;
    }

    Methods emlMethods = new Methods();
    List<JAXBElement<?>> parts = emlMethods.getMethodStepAndSamplingAndQualityControl();

    if (methods.methodSteps() != null) {
      for (String methodStep : methods.methodSteps()) {
        parts.add(OBJECT_FACTORY.createMethodsMethodStep(toDescription(methodStep)));
      }
    }

    if (methods.sampling() != null) {
      BaseDatasetDto.Sampling sampling = methods.sampling();
      if (sampling.studyExtent() != null || sampling.samplingDescription() != null) {
        Methods.Sampling emlSampling = OBJECT_FACTORY.createMethodsSampling();
        if (sampling.studyExtent() != null) {
          emlSampling.setStudyExtent(toDescription(sampling.studyExtent()));
        }
        if (sampling.samplingDescription() != null) {
          Methods.Sampling.SamplingDescription samplingDescription =
              OBJECT_FACTORY.createMethodsSamplingSamplingDescription();
          samplingDescription.setPara(toPara(sampling.samplingDescription()));
          emlSampling.setSamplingDescription(samplingDescription);
        }
        parts.add(OBJECT_FACTORY.createMethodsSampling(emlSampling));
      }
    }

    if (methods.qualityControlDescriptions() != null) {
      for (String qualityControl : methods.qualityControlDescriptions()) {
        parts.add(OBJECT_FACTORY.createMethodsQualityControl(toDescription(qualityControl)));
      }
    }

    return emlMethods;
  }

  private ProjectType buildProject(BaseDatasetDto.Project project) {
    if (project == null) {
      return null;
    }

    ProjectType emlProject = new ProjectType();

    if (project.title() != null) {
      I18NString title = new I18NString();
      title.setValue(project.title());
      emlProject.setTitle(title);
    }

    if (project.abstractText() != null) {
      TextType abstractText = new TextType();
      abstractText.getContent().add(project.abstractText());
      emlProject.setAbstract(abstractText);
    }

    if (project.funding() != null) {
      ProjectType.Funding funding = OBJECT_FACTORY.createProjectTypeFunding();
      funding.setPara(toPara(project.funding()));
      emlProject.setFunding(funding);
    }

    if (project.personnel() != null) {
      for (AgentRoles agentRoles : project.personnel()) {
        AgentWithRoleType personnel = toAgentWithRoleType(agentRoles);
        if (personnel != null) {
          emlProject.getPersonnel().add(personnel);
        }
      }
    }

    if (project.awards() != null) {
      for (BaseDatasetDto.Award award : project.awards()) {
        emlProject.getAward().add(toAwardType(award));
      }
    }

    // TODO: Map studyAreaDescription if/when DataSetDto supports EML Descriptor.

    // if (project.studyAreaDescription() != null) {
    //   ProjectType.StudyAreaDescription studyAreaDescription =
    //       OBJECT_FACTORY.createProjectTypeStudyAreaDescription();
    //   Descriptor descriptor = OBJECT_FACTORY.createDescriptor();
    //   descriptor.setDescriptorValue(project.studyAreaDescription());
    //   studyAreaDescription.setDescriptor(descriptor);
    //   emlProject.setStudyAreaDescription(studyAreaDescription);
    // }

    if (project.designDescription() != null) {
      emlProject.setDesignDescription(toDescription(project.designDescription()));
    }

    return emlProject;
  }

  private AgentWithRoleType toAgentWithRoleType(AgentRoles agentRoles) {
    AgentWithRoleType agentWithRoleType = OBJECT_FACTORY.createAgentWithRoleType();
    populateAgentType(agentWithRoleType, agentRoles);
    if (agentWithRoleType.getOrganizationNameOrIndividualNameOrPositionName().isEmpty()) {
      return null;
    }
    if (agentRoles.getRoles() != null && !agentRoles.getRoles().isEmpty()) {
      agentWithRoleType.setRole(agentRoles.getRoles().getFirst());
    }
    return agentWithRoleType;
  }

  private static AwardType toAwardType(BaseDatasetDto.Award award) {
    AwardType awardType = OBJECT_FACTORY.createAwardType();
    awardType.setFunderName(award.funderName());
    if (award.funderIdentifiers() != null) {
      awardType.getFunderIdentifier().addAll(award.funderIdentifiers());
    }
    awardType.setAwardNumber(award.awardNumber());
    awardType.setTitle(award.title());
    awardType.setAwardUrl(award.awardUrl());
    return awardType;
  }

  private static Description toDescription(String text) {
    if (text == null) {
      return null;
    }
    Description description = OBJECT_FACTORY.createDescription();
    Description2 description2 = OBJECT_FACTORY.createDescription2();
    description2.getContent().add(toPara(text));
    description.setDescription(description2);
    return description;
  }

  private static Para toPara(String text) {
    Para para = OBJECT_FACTORY.createPara();
    para.getContent().add(text);
    return para;
  }

  private static Coverage buildCoverage(BaseDatasetDto.Coverage coverage) {
    if (coverage == null) {
      return null;
    }

    Coverage emlCoverage = new Coverage();

    if (coverage.geographic() != null) {
      emlCoverage.getGeographicCoverageOrTemporalCoverageOrTaxonomicCoverage()
          .add(buildGeographicCoverage(coverage.geographic()));
    }

    if (coverage.temporal() != null) {
      TemporalCoverage temporalCoverage = buildTemporalCoverage(coverage.temporal());
      if (temporalCoverage != null) {
        emlCoverage.getGeographicCoverageOrTemporalCoverageOrTaxonomicCoverage()
            .add(temporalCoverage);
      }
    }

    if (coverage.taxonomic() != null) {
      for (BaseDatasetDto.TaxonomicCoverage taxonomic : coverage.taxonomic()) {
        emlCoverage.getGeographicCoverageOrTemporalCoverageOrTaxonomicCoverage()
            .add(buildTaxonomicCoverage(taxonomic));
      }
    }

    return emlCoverage;
  }

  private static GeographicCoverage buildGeographicCoverage(BaseDatasetDto.GeographicCoverage geographic) {
    GeographicCoverage emlGeographic = new GeographicCoverage();
    emlGeographic.setGeographicDescription(geographic.geographicDescription());

    if (geographic.boundingBox() != null) {
      BaseDatasetDto.BoundingBox boundingBox = geographic.boundingBox();
      GeographicCoverage.BoundingCoordinates boundingCoordinates = new GeographicCoverage.BoundingCoordinates();
      boundingCoordinates.setWestBoundingCoordinate(BigDecimal.valueOf(boundingBox.west()));
      boundingCoordinates.setEastBoundingCoordinate(BigDecimal.valueOf(boundingBox.east()));
      boundingCoordinates.setNorthBoundingCoordinate(BigDecimal.valueOf(boundingBox.north()));
      boundingCoordinates.setSouthBoundingCoordinate(BigDecimal.valueOf(boundingBox.south()));
      emlGeographic.setBoundingCoordinates(boundingCoordinates);
    }

    return emlGeographic;
  }

  
  private static TemporalCoverage buildTemporalCoverage(BaseDatasetDto.TemporalCoverage temporal) {
    LocalDate begin = temporal.beginDate();
    LocalDate end = temporal.endDate();
    if (begin == null && end == null) {
      return null;
    }

    TemporalCoverage emlTemporal = new TemporalCoverage();
    if (begin != null && end != null) {
      TemporalCoverage.RangeOfDates rangeOfDates = new TemporalCoverage.RangeOfDates();
      rangeOfDates.setBeginDate(toCalendarDate(begin));
      rangeOfDates.setEndDate(toCalendarDate(end));
      emlTemporal.setRangeOfDates(rangeOfDates);
    } else {
      emlTemporal.setSingleDateTime(toCalendarDate(begin != null ? begin : end));
    }
    return emlTemporal;
  }

  private static CalendarDate toCalendarDate(LocalDate date) {
    CalendarDate calendarDate = new CalendarDate();
    calendarDate.setCalendarDate(date.toString());
    return calendarDate;
  }

  private static TaxonomicCoverage buildTaxonomicCoverage(BaseDatasetDto.TaxonomicCoverage taxonomic) {
    TaxonomicCoverage emlTaxonomic = new TaxonomicCoverage();

    TaxonomicCoverage.TaxonomicClassification classification = new TaxonomicCoverage.TaxonomicClassification();
    classification.setTaxonRankName(taxonomic.rank());
    classification.setTaxonRankValue(taxonomic.scientificName());
    classification.setCommonName(taxonomic.commonName());
    emlTaxonomic.getTaxonomicClassification().add(classification);

    return emlTaxonomic;
  }

}

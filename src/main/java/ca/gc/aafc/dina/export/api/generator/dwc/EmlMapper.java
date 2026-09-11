package ca.gc.aafc.dina.export.api.generator.dwc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ca.aafc.eml.generated.eml.AgentType;
import ca.aafc.eml.generated.eml.CalendarDate;
import ca.aafc.eml.generated.eml.Coverage;
import ca.aafc.eml.generated.eml.Dataset;
import ca.aafc.eml.generated.eml.Eml;
import ca.aafc.eml.generated.eml.GeographicCoverage;
import ca.aafc.eml.generated.eml.I18NString;
import ca.aafc.eml.generated.eml.IndividualName;
import ca.aafc.eml.generated.eml.IntellectualRights;
import ca.aafc.eml.generated.eml.KeywordSet;
import ca.aafc.eml.generated.eml.Licensed;
import ca.aafc.eml.generated.eml.ObjectFactory;
import ca.aafc.eml.generated.eml.Para;
import ca.aafc.eml.generated.eml.TaxonomicCoverage;
import ca.aafc.eml.generated.eml.TemporalCoverage;
import ca.aafc.eml.generated.eml.TextType;
import ca.aafc.eml.generated.eml.Ulink;
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
   * @return an EML document wrapping the mapped dataset
   */
  public Eml datasetToEml(BaseDatasetDto dataset) {
    Eml eml = new Eml();

    eml.setPackageId(dataset.getUuid().toString());
    eml.getSystem().add(EML_SYSTEM);

    Dataset emlDataset = new Dataset();

    if (dataset.getUuid() != null) {
      emlDataset.getAlternateIdentifier().add(dataset.getUuid().toString());
    }

    mapTitles(dataset, emlDataset);
    mapAbstract(dataset, emlDataset);
    mapKeywordSets(dataset, emlDataset);
    mapRights(dataset, emlDataset);
    mapAgents(dataset, emlDataset);
    emlDataset.setCoverage(buildCoverage(dataset.getCoverage()));

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
      emlKeywordSet.setKeywordThesaurus(keywordSet.thesaurus());
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
        .findFirst();
    Optional<AgentType> metadataProvider = dataset.getAgentRoles().stream()
        .filter(agentRole -> hasRole(agentRole, BaseDatasetDto.AGENT_ROLE_METADATA_PROVIDER))
        .map(this::toAgentType)
        .findFirst();

    creator.ifPresent(c -> {
      emlDataset.getCreator().add(c);
      emlDataset.getContact().add(c);
    });
    metadataProvider.ifPresent(mp -> emlDataset.getMetadataProvider().add(mp));

    if (emlDataset.getContact().isEmpty()) {
      metadataProvider.ifPresent(mp -> emlDataset.getContact().add(mp));
    }
  }

  private static boolean hasRole(AgentRoles agentRoles, String role) {
    return agentRoles.getRoles() != null && agentRoles.getRoles().contains(role);
  }

  private AgentType toAgentType(AgentRoles agentRoles) {
    AgentType agentType = new AgentType();

    UUID agentUuid = agentRoles.getAgent();
    if (agentUuid != null) {
      agentType.getId().add(agentUuid.toString());
    }

    JsonApiDocument agentDoc = resolveAgent(agentUuid);
    if (agentDoc == null || agentDoc.getAttributes() == null) {
      return agentType;
    }

    Map<String, Object> attributes = agentDoc.getAttributes();
    String displayName = text(attributes.get("displayName"));

    if ("organization".equals(agentDoc.getType())) {
      if (displayName != null) {
        agentType.getOrganizationNameOrIndividualNameOrPositionName().add(
          OBJECT_FACTORY.createAgentTypeOrganizationName(displayName));
      }
    } else {
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
    }

    String email = text(attributes.get("email"));
    if (email != null) {
      agentType.getElectronicMailAddress().add(email);
    }

    String webpage = text(attributes.get("webpage"));
    if (webpage != null) {
      agentType.getOnlineUrl().add(webpage);
    }
    return agentType;
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }

  private JsonApiDocument resolveAgent(UUID agentUuid) {
    if (agentUuid == null) {
      return null;
    }
    JsonApiDocument person = fetchAgentDocument(agentApiUrl + "/person/" + agentUuid);
    return person != null ? person : fetchAgentDocument(agentApiUrl + "/organization/" + agentUuid);
  }

  private JsonApiDocument fetchAgentDocument(String url) {
    HttpUrl httpUrl = HttpUrl.parse(url);
    return httpUrl == null ? null : dinaApiClient.fetchDocument(httpUrl);
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

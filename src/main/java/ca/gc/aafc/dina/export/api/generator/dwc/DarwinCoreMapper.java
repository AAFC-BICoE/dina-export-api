package ca.gc.aafc.dina.export.api.generator.dwc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import ca.aafc.eml.generated.eml.AgentType;
import ca.aafc.eml.generated.eml.CalendarDate;
import ca.aafc.eml.generated.eml.Coverage;
import ca.aafc.eml.generated.eml.Dataset;
import ca.aafc.eml.generated.eml.Eml;
import ca.aafc.eml.generated.eml.GeographicCoverage;
import ca.aafc.eml.generated.eml.I18NString;
import ca.aafc.eml.generated.eml.IntellectualRights;
import ca.aafc.eml.generated.eml.KeywordSet;
import ca.aafc.eml.generated.eml.Licensed;
import ca.aafc.eml.generated.eml.Para;
import ca.aafc.eml.generated.eml.TaxonomicCoverage;
import ca.aafc.eml.generated.eml.TemporalCoverage;
import ca.aafc.eml.generated.eml.TextType;
import ca.gc.aafc.dina.dto.BaseDatasetDto;
import ca.gc.aafc.dina.entity.AgentRoles;
import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
import ca.gc.aafc.dina.export.api.generator.helper.ApiReferenceResolver;
import ca.gc.aafc.dina.i18n.MultilingualDescription;
import ca.gc.aafc.dina.i18n.MultilingualTitle;
import ca.gc.aafc.dina.json.JsonHelper;
import lombok.extern.log4j.Log4j2;

/**
 * Maps DINA resource to DarwinCore concepts
 *
 * For each column mapping, extracts the value from the resource context.
 *
 * Handles:
 * - Simple field extraction via dot notation
 * - Array filtering (e.g., isPrimary == true)
 * - Static values
 * - Null safety and error handling
 */
@Component
@Log4j2
public class DarwinCoreMapper {

  /**
   * DINA role designating the dataset's responsible party. Agents holding this
   * role are mapped to the EML creator, metadataProvider and contact fields.
   */
  public static final String SUPER_USER_ROLE = "SUPER_USER";

  private final ApiReferenceResolver apiReferenceResolver;

  public DarwinCoreMapper(ApiReferenceResolver apiReferenceResolver) {
    this.apiReferenceResolver = apiReferenceResolver;
  }

  /**
   * Maps a DINA {@link BaseDatasetDto} resource into a schema derived EML
   * {@link Dataset}.
   *
   * <p>Agents holding the {@link #SUPER_USER_ROLE} role are mapped to the EML
   * creator, metadataProvider and contact fields. Agent details (names, email,
   * position) are not populated yet since {@link BaseDatasetDto} only holds
   * agent UUIDs; only the UUID is retained as a reference.</p>
   *
   * @param dataset the DINA dataset to map
   * @return an EML document wrapping the mapped dataset
   */
  public Eml datasetToEml(BaseDatasetDto dataset) {
    Eml eml = new Eml();
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

  private void mapTitles(BaseDatasetDto dataset, Dataset emlDataset) {
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

  private void mapAbstract(BaseDatasetDto dataset, Dataset emlDataset) {
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

  private void mapKeywordSets(BaseDatasetDto dataset, Dataset emlDataset) {
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

  private void mapRights(BaseDatasetDto dataset, Dataset emlDataset) {
    BaseDatasetDto.UsageRights usageRights = dataset.getUsageRights();
    if (usageRights == null) {
      return;
    }

    Licensed licensed = new Licensed();
    licensed.setLicenseName(usageRights.licenseName());
    licensed.setUrl(usageRights.licenseUrl());
    emlDataset.setLicensed(licensed);

    if (usageRights.usageTerms() != null && !usageRights.usageTerms().isBlank()) {
      IntellectualRights intellectualRights = new IntellectualRights();
      Para para = new Para();
      para.getContent().add(usageRights.usageTerms());
      intellectualRights.setPara(para);
      emlDataset.setIntellectualRights(intellectualRights);
    }
  }

  private void mapAgents(BaseDatasetDto dataset, Dataset emlDataset) {
    if (dataset.getAgentRoles() == null) {
      return;
    }

    List<AgentType> responsibleParties = dataset.getAgentRoles().stream()
        .filter(agentRole -> hasRole(agentRole, SUPER_USER_ROLE))
        .map(this::toAgentType)
        .toList();

    emlDataset.getCreator().addAll(responsibleParties);
    emlDataset.getMetadataProvider().addAll(responsibleParties);
    emlDataset.getContact().addAll(responsibleParties);
  }

  private static boolean hasRole(AgentRoles agentRoles, String role) {
    return agentRoles.getRoles() != null && agentRoles.getRoles().contains(role);
  }

  private AgentType toAgentType(AgentRoles agentRoles) {
    AgentType agentType = new AgentType();
    // Agent details (names, email, position) require resolving the referenced agent.
    // Until that resolution exists, only the DINA agent UUID is retained.
    if (agentRoles.getAgent() != null) {
      agentType.getId().add(agentRoles.getAgent().toString());
    }
    return agentType;
  }

  private Coverage buildCoverage(BaseDatasetDto.Coverage coverage) {
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

  private GeographicCoverage buildGeographicCoverage(BaseDatasetDto.GeographicCoverage geographic) {
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

  private TemporalCoverage buildTemporalCoverage(BaseDatasetDto.TemporalCoverage temporal) {
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

  private CalendarDate toCalendarDate(LocalDate date) {
    CalendarDate calendarDate = new CalendarDate();
    calendarDate.setCalendarDate(date.toString());
    return calendarDate;
  }

  private TaxonomicCoverage buildTaxonomicCoverage(BaseDatasetDto.TaxonomicCoverage taxonomic) {
    TaxonomicCoverage emlTaxonomic = new TaxonomicCoverage();

    TaxonomicCoverage.TaxonomicClassification classification = new TaxonomicCoverage.TaxonomicClassification();
    classification.setTaxonRankName(taxonomic.rank());
    classification.setTaxonRankValue(taxonomic.scientificName());
    classification.setCommonName(taxonomic.commonName());
    emlTaxonomic.getTaxonomicClassification().add(classification);

    return emlTaxonomic;
  }

  /**
   * Extract a DarwinCore term value from the entities context
   *
   * Processing order (first match wins):
   * 1. Static value (if defined)
   * 2. Array filtering (if filter is defined)
   * 3. Simple path navigation
   *
   * @param entitiesContext Map of entity contexts built by DarwinCoreContextBuilder
   * @param mapping The column mapping configuration from YAML
   * @return The extracted value, or null if not found
   *
   * Example:
   * mapping = {
   *   dwcTerm: "kingdom",
   *   context: "determination",
   *   source: "kingdom"
   * }
   * Result: Gets the kingdom value from the determination context
   */
  public String extractValue(Map<String, JsonNode> entitiesContext, DarwinCoreExportConfig.ColumnMapping mapping) {
    // 1. Static value (highest priority)
    if (mapping.getStaticValue() != null) {
      log.debug("Using static value for {}: {}", mapping.getDwcTerm(), mapping.getStaticValue());
      return mapping.getStaticValue();
    }

    // 2. Get context node from entities map
    JsonNode contextNode = entitiesContext.get(mapping.getContext());
    if (contextNode == null) {
      log.debug("Context not found: {} for DwC term: {}", mapping.getContext(), mapping.getDwcTerm());
      return null;
    }

    // 3. Filter + optional subpath (JSONPath filter syntax, e.g. @.placeType == 'county')
    if (mapping.getFilter() != null) {

      //Sanity check. Make sure the source exists.
      JsonNode checkNode = contextNode.at("/" + mapping.getSource());
      if (checkNode.isMissingNode() || checkNode.isNull()) {
        return null;
      }

      String expression = "$." + mapping.getSource() + "[?(" + mapping.getFilter() + ")]"
          + (mapping.getPath() != null ? "." + mapping.getPath() : "");
      JsonNode result = JsonHelper.findOneInJsonNode(contextNode, expression);
      return result != null && !result.isNull() ? result.asText() : null;
    }

    if (mapping.getApiReference() != null) {
      List<String> resolvedValues = apiReferenceResolver.resolveApiReferencedValues(contextNode, mapping.getApiReference(), mapping.getSource());
      return resolvedValues.isEmpty() ? null : String.join(mapping.getSeparator(), resolvedValues);
    }

    // Collect the value
    // from each element and join them with the configured separator.
    if (contextNode.isArray()) {
      return joinArrayValues((ArrayNode) contextNode, mapping.getSource(), mapping.getSeparator());
    }
    JsonNode value = JsonHelper.findOneInJsonNode(contextNode, "$." + mapping.getSource());
    return value != null && !value.isNull() ? value.asText() : null;
  }

  /**
   * Extracts a value from each element of a to-many context and joins them.
   *
   * @param array to-many context array
   * @param source dot-notation path relative to each array element
   * @param separator separator to use between values
   * @return joined text or null if no non-null value was found
   */
  private static String joinArrayValues(ArrayNode array, String source, String separator) {
    StringBuilder sb = new StringBuilder();
    for (JsonNode element : array) {
      JsonNode value = JsonHelper.findOneInJsonNode(element, "$." + source);
      if (value == null || value.isNull()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(separator);
      }
      sb.append(value.asText());
    }
    return sb.length() > 0 ? sb.toString() : null;
  }

}

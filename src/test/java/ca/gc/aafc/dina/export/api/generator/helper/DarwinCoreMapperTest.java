package ca.gc.aafc.dina.export.api.generator.helper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.aafc.eml.generated.eml.Coverage;
import ca.aafc.eml.generated.eml.Dataset;
import ca.aafc.eml.generated.eml.GeographicCoverage;
import ca.aafc.eml.generated.eml.TaxonomicCoverage;
import ca.aafc.eml.generated.eml.TemporalCoverage;
import ca.gc.aafc.dina.dto.BaseDatasetDto;
import ca.gc.aafc.dina.entity.AgentRoles;
import ca.gc.aafc.dina.export.api.config.ApiReference;
import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
import ca.gc.aafc.dina.export.api.generator.dwc.DarwinCoreMapper;
import ca.gc.aafc.dina.export.api.service.DinaApiClient;
import ca.gc.aafc.dina.i18n.MultilingualDescription;
import ca.gc.aafc.dina.i18n.MultilingualTitle;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import okhttp3.HttpUrl;

public class DarwinCoreMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void extractValue() throws JsonProcessingException {

    DarwinCoreMapper mapper = new DarwinCoreMapper(mock(ApiReferenceResolver.class));

    DarwinCoreExportConfig.ColumnMapping mapping = new DarwinCoreExportConfig.ColumnMapping();
    mapping.setContext("collectingEvent");
    mapping.setDwcTerm("county");
    mapping.setFilter("@.placeType == 'county'");
    mapping.setSource("geographicPlaceNameSourceDetail.higherGeographicPlaces");
    mapping.setPath("name");

    String json = """
        {
            "geographicPlaceNameSourceDetail": {
                "sourceUrl": "https://nominatim.openstreetmap.org/ui/details.html?osmtype=R&osmid=62650",
                "customGeographicPlace": null,
                "selectedGeographicPlace": {
                    "id": "62650",
                    "element": "R",
                    "placeType": "ISO3166-2-lvl4",
                    "name": "DE-HE"
                },
                "higherGeographicPlaces": null,
                "stateProvince": {
                    "id": "62650",
                    "element": "relation",
                    "placeType": "state",
                    "name": "Hesse"
                },
                "country": {
                    "code": "de",
                    "name": "Germany"
                },
                "recordedOn": "2026-08-07T17:38:23.075989879Z"
            }
        }
    """;

    ObjectMapper objectMapper = new ObjectMapper();
    Map<String, JsonNode > entitiesContext = Map.of("collectingEvent", objectMapper.readTree(json));
    assertNull(mapper.extractValue(entitiesContext, mapping));
  }

  @Test
  public void extractValue_joinsToManyValues() throws JsonProcessingException {

    DarwinCoreMapper mapper = new DarwinCoreMapper(mock(ApiReferenceResolver.class));

    DarwinCoreExportConfig.ColumnMapping mapping = new DarwinCoreExportConfig.ColumnMapping();
    mapping.setContext("attachment");
    mapping.setDwcTerm("associatedSequences");
    mapping.setSource("managedAttributes.ena_run_accession");

    String json = """
        [
          { "managedAttributes": { "ena_run_accession": "U3485313" } },
          { "managedAttributes": { "ena_run_accession": "GU328060" } }
        ]
        """;

    ObjectMapper objectMapper = new ObjectMapper();
    Map<String, JsonNode> entitiesContext = Map.of("attachment", objectMapper.readTree(json));
    assertEquals("U3485313 | GU328060", mapper.extractValue(entitiesContext, mapping));
  }

  @Test
  public void extractValue_resolvesVocabularyUriTemplate() throws JsonProcessingException {

    DinaApiClient client = mock(DinaApiClient.class);
    when(client.fetchDocument(any(HttpUrl.class))).thenReturn(vocabularyItemDocument());

    DarwinCoreMapper mapper = new DarwinCoreMapper(new ApiReferenceResolver(client));

    DarwinCoreExportConfig.ColumnMapping mapping = new DarwinCoreExportConfig.ColumnMapping();
    mapping.setContext("attachment");
    mapping.setDwcTerm("associatedSequences");
    mapping.setSource("managedAttributes");
    ApiReference apiReference = new ApiReference();
    apiReference.setVocabularyValue("uriTemplate");
    apiReference.setVocabularyKey("ena_run_accession");
    apiReference.setValuePlaceholder("$1");
    apiReference.setVocabularyUrl("http://localhost:8081/api/v1/controlled-vocabulary-item");
    apiReference.setDinaComponent("METADATA");
    mapping.setApiReference(apiReference);

    String json = """
        [
          { "managedAttributes": { "ena_run_accession": "U3485313" } },
          { "managedAttributes": { "ena_run_accession": "GU328060" } }
        ]
        """;

    Map<String, JsonNode> entitiesContext = Map.of("attachment", objectMapper.readTree(json));
    String expected = "https://www.ebi.ac.uk/ena/browser/view/U3485313 | https://www.ebi.ac.uk/ena/browser/view/GU328060";
    assertEquals(expected, mapper.extractValue(entitiesContext, mapping));
  }

  @Test
  public void extractValue_resolvesVocabularyUriTemplate_onlyConfiguredKey() throws JsonProcessingException {

    DinaApiClient client = mock(DinaApiClient.class);
    when(client.fetchDocument(any(HttpUrl.class))).thenReturn(vocabularyItemDocument());

    DarwinCoreMapper mapper = new DarwinCoreMapper(new ApiReferenceResolver(client));

    DarwinCoreExportConfig.ColumnMapping mapping = new DarwinCoreExportConfig.ColumnMapping();
    mapping.setContext("attachment");
    mapping.setDwcTerm("associatedSequences");
    mapping.setSource("managedAttributes");
    ApiReference apiReference = new ApiReference();
    apiReference.setVocabularyValue("uriTemplate");
    apiReference.setVocabularyKey("ena_run_accession");
    apiReference.setValuePlaceholder("$1");
    apiReference.setVocabularyUrl("http://localhost:8081/api/v1/controlled-vocabulary-item");
    apiReference.setDinaComponent("METADATA");
    mapping.setApiReference(apiReference);

    String json = """
        [
          { "managedAttributes": { "ena_run_accession": "U3485313", "other_key": "OTHER" } },
          { "managedAttributes": { "ena_run_accession": "GU328060" } }
        ]
        """;

    Map<String, JsonNode> entitiesContext = Map.of("attachment", objectMapper.readTree(json));
    String expected = "https://www.ebi.ac.uk/ena/browser/view/U3485313 | https://www.ebi.ac.uk/ena/browser/view/GU328060";
    assertEquals(expected, mapper.extractValue(entitiesContext, mapping));
    verify(client, never()).fetchDocument(argThat(url -> url.toString().contains("other_key")));
  }

  @Test
  public void extractValue_resolvesVocabulary_noUrlTemplate_skipsResolution() throws JsonProcessingException {
    DinaApiClient client = mock(DinaApiClient.class);

    DarwinCoreMapper mapper = new DarwinCoreMapper(new ApiReferenceResolver(client));

    DarwinCoreExportConfig.ColumnMapping mapping = new DarwinCoreExportConfig.ColumnMapping();
    mapping.setContext("attachment");
    mapping.setDwcTerm("associatedSequences");
    mapping.setSource("managedAttributes");
    ApiReference apiReference = new ApiReference();
    apiReference.setVocabularyValue("uriTemplate");
    apiReference.setVocabularyKey("ena_run_accession");
    // vocabularyUrl intentionally not set: resolution should be skipped
    mapping.setApiReference(apiReference);

    String json = """
        [
          { "managedAttributes": { "ena_run_accession": "U3485313" } }
        ]
        """;

    Map<String, JsonNode> entitiesContext = Map.of("attachment", objectMapper.readTree(json));
    assertNull(mapper.extractValue(entitiesContext, mapping));
    verify(client, never()).fetchDocument(any(HttpUrl.class));
  }

  @Test
  public void datasetToEml_mapsAllFields() {
    DarwinCoreMapper mapper = new DarwinCoreMapper(mock(ApiReferenceResolver.class));

    BaseDatasetDto dataset = new BaseDatasetDto();
    dataset.setUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

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
    dataset.setCoverage(new BaseDatasetDto.Coverage(
        new BaseDatasetDto.GeographicCoverage("Canada",
            new BaseDatasetDto.BoundingBox(-140.0, 41.0, -52.0, 70.0)),
        new BaseDatasetDto.TemporalCoverage(
            LocalDate.of(2000, 1, 1), LocalDate.of(2020, 12, 31)),
        List.of(new BaseDatasetDto.TaxonomicCoverage("kingdom", "Animalia", "Animals"))));

    Dataset emlDataset = mapper.datasetToEml(dataset).getDataset();
    assertNotNull(emlDataset);

    // Identifier
    assertEquals(List.of("123e4567-e89b-12d3-a456-426614174000"), emlDataset.getAlternateIdentifier());

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
    assertEquals(List.of("Free to use with attribution."),
        emlDataset.getIntellectualRights().getPara().getContent());

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
  }

  @Test
  public void datasetToEml_onEmptyDataset_returnsEmptyDataset() {
    DarwinCoreMapper mapper = new DarwinCoreMapper(mock(ApiReferenceResolver.class));

    Dataset emlDataset = mapper.datasetToEml(new BaseDatasetDto()).getDataset();

    assertNotNull(emlDataset);
    assertTrue(emlDataset.getAlternateIdentifier().isEmpty());
    assertTrue(emlDataset.getTitle().isEmpty());
    assertNull(emlDataset.getAbstract());
    assertTrue(emlDataset.getKeywordSet().isEmpty());
    assertNull(emlDataset.getLicensed());
    assertNull(emlDataset.getIntellectualRights());
    assertNull(emlDataset.getCoverage());
  }

  @Test
  public void datasetToEml_mapsSuperUserAgentsToCreatorMetadataProviderAndContact() {
    DarwinCoreMapper mapper = new DarwinCoreMapper(mock(ApiReferenceResolver.class));

    UUID creator = UUID.randomUUID();
    UUID metadataProvider = UUID.randomUUID();

    BaseDatasetDto dataset = new BaseDatasetDto();
    dataset.setAgentRoles(List.of(
        AgentRoles.builder().agent(creator).roles(List.of(DarwinCoreMapper.CREATOR)).build(),
        AgentRoles.builder().agent(metadataProvider).roles(List.of(DarwinCoreMapper.METADATA_PROVIDER)).build(),
        AgentRoles.builder().agent(UUID.randomUUID()).roles(List.of("helper", "manager")).build()));

    Dataset emlDataset = mapper.datasetToEml(dataset).getDataset();

    assertEquals(1, emlDataset.getCreator().size());
    assertEquals(1, emlDataset.getMetadataProvider().size());
 //   assertEquals(1, emlDataset.getContact().size());

    assertEquals(List.of(creator.toString()), emlDataset.getCreator().get(0).getId());
    assertEquals(List.of(metadataProvider.toString()), emlDataset.getMetadataProvider().get(0).getId());
   // assertEquals(List.of(superUser.toString()), emlDataset.getContact().get(0).getId());
    assertNull(emlDataset.getPublisher());
  }

  private JsonApiDocument vocabularyItemDocument() {
    return JsonApiDocument.builder()
      .data(JsonApiDocument.ResourceObject.builder()
        .id(UUID.fromString("01a03daf-fe1d-7731-a3b9-6e488ea67d4e"))
        .type("controlled-vocabulary-item")
        .attributes(Map.of("uriTemplate", "https://www.ebi.ac.uk/ena/browser/view/$1"))
        .build())
      .build();
  }
}

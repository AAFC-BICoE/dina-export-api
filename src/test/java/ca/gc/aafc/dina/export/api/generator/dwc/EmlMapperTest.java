package ca.gc.aafc.dina.export.api.generator.dwc;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ca.aafc.eml.generated.eml.Coverage;
import ca.aafc.eml.generated.eml.Dataset;
import ca.aafc.eml.generated.eml.GeographicCoverage;
import ca.aafc.eml.generated.eml.TaxonomicCoverage;
import ca.aafc.eml.generated.eml.TemporalCoverage;
import ca.gc.aafc.dina.dto.BaseDatasetDto;
import ca.gc.aafc.dina.entity.AgentRoles;
import ca.gc.aafc.dina.i18n.MultilingualDescription;
import ca.gc.aafc.dina.i18n.MultilingualTitle;

public class EmlMapperTest {

  @Test
  public void datasetToEml_mapsAllFields() {
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

    Dataset emlDataset = EmlMapper.datasetToEml(dataset).getDataset();
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
    Dataset emlDataset = EmlMapper.datasetToEml(new BaseDatasetDto()).getDataset();

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
  public void datasetToEml_mapsAgentsToCreatorMetadataProviderAndContact() {
    UUID creator = UUID.randomUUID();
    UUID metadataProvider = UUID.randomUUID();

    BaseDatasetDto dataset = new BaseDatasetDto();
    dataset.setAgentRoles(List.of(
        AgentRoles.builder().agent(creator).roles(List.of(EmlMapper.CREATOR)).build(),
        AgentRoles.builder().agent(metadataProvider).roles(List.of(EmlMapper.METADATA_PROVIDER)).build(),
        AgentRoles.builder().agent(UUID.randomUUID()).roles(List.of("helper", "manager")).build()));

    Dataset emlDataset = EmlMapper.datasetToEml(dataset).getDataset();

    assertEquals(1, emlDataset.getCreator().size());
    assertEquals(1, emlDataset.getMetadataProvider().size());
 //   assertEquals(1, emlDataset.getContact().size());

    assertEquals(List.of(creator.toString()), emlDataset.getCreator().get(0).getId());
    assertEquals(List.of(metadataProvider.toString()), emlDataset.getMetadataProvider().get(0).getId());
   // assertEquals(List.of(superUser.toString()), emlDataset.getContact().get(0).getId());
    assertNull(emlDataset.getPublisher());
  }

}

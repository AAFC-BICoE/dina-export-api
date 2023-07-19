package ca.gc.aafc.dina.export.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.ElasticSearchTestContainerInitializer;
import ca.gc.aafc.dina.export.api.dto.ExportRequestDto;
import ca.gc.aafc.dina.export.api.testsupport.jsonapi.JsonApiDocuments;
import ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;

@ContextConfiguration(initializers = { ElasticSearchTestContainerInitializer.class })
public class ExportRequestRepositoryIT extends BaseIntegrationTest {

  private static final String MAT_SAMPLE_INDEX = "dina_material_sample_index";

  @Inject
  private ExportRequestRepository exportRepo;

  @Inject
  private ElasticsearchClient esClient;

  @Test
  public void testESDatasource() throws IOException {

    ElasticSearchTestUtils.createIndex(esClient, MAT_SAMPLE_INDEX, "elasticsearch/material_sample_index_settings.json");

    UUID docId = UUID.randomUUID();
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId.toString(), JsonApiDocuments.getMaterialSampleDocument(docId));

    String query = "{\"query\": {\"match_all\": {}}, \"sort\": [{\"data.attributes.createdOn\": \"asc\"}]}";

    ExportRequestDto dto = new ExportRequestDto();
    dto.setSource(MAT_SAMPLE_INDEX);
    dto.setQuery(query);
    dto.setColumns(List.of("materialSampleName", "collectingEvent.dwcVerbatimLocality"));
    exportRepo.create(dto);

    assertNotNull(dto.getUuid());
  }

}

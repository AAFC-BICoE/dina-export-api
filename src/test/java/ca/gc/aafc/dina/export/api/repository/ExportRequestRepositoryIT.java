package ca.gc.aafc.dina.export.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.ElasticSearchTestContainerInitializer;
import ca.gc.aafc.dina.export.api.dto.ExportRequestDto;
import ca.gc.aafc.dina.export.api.file.FileController;
import ca.gc.aafc.dina.export.api.testsupport.jsonapi.JsonApiDocuments;
import ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;

@ContextConfiguration(initializers = { ElasticSearchTestContainerInitializer.class })
public class ExportRequestRepositoryIT extends BaseIntegrationTest {

  private static final String MAT_SAMPLE_INDEX = "dina_material_sample_index";

  @Inject
  private ExportRequestRepository exportRepo;

  @Inject
  private FileController fileController;

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
    dto.setColumns(List.of("materialSampleName", "collectingEvent.dwcVerbatimLocality",
      "dwcCatalogNumber", "dwcOtherCatalogNumbers", "managedAttributes.attribute_1"));
    exportRepo.create(dto);
    assertNotNull(dto.getUuid());

    ResponseEntity<InputStreamResource>
      response = fileController.downloadFile(dto.getUuid(), FileController.DownloadType.DATA_EXPORT);

    assertNotNull(response.getBody());
    String text = new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    List<String> lines = text.lines().toList();
    // make sure managedAttributes are extracted to specific column(s)
    assertTrue(lines.get(0).contains("managedAttributes.attribute_1"));

    // check that arrays are exported using ; as element separator
    assertTrue(lines.get(1).contains("cn1;cn1-1"));

  }

}

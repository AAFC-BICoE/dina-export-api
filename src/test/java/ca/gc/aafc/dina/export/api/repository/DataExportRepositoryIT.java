package ca.gc.aafc.dina.export.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.ElasticSearchTestContainerInitializer;
import ca.gc.aafc.dina.export.api.async.AsyncConsumer;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.dto.DataExportDto;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.file.FileController;
import ca.gc.aafc.dina.export.api.testsupport.jsonapi.JsonApiDocuments;
import ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.crnk.core.exception.ResourceNotFoundException;
import io.crnk.core.queryspec.QuerySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.inject.Inject;

@ContextConfiguration(initializers = { ElasticSearchTestContainerInitializer.class })
public class DataExportRepositoryIT extends BaseIntegrationTest {

  private static final String MAT_SAMPLE_INDEX = "dina_material_sample_index";

  @Inject
  private DataExportConfig dataExportConfig;

  @Inject
  private DataExportRepository dataExportRepository;

  @Inject
  private FileController fileController;

  @Inject
  private ElasticsearchClient esClient;

  @Inject
  private AsyncConsumer<Future<UUID>> asyncConsumer;

  @Test
  public void testESDatasource() throws IOException {

    ElasticSearchTestUtils.createIndex(esClient, MAT_SAMPLE_INDEX, "elasticsearch/material_sample_index_settings.json");

    // Add 2 documents with  page size of 1 (from application-test.yml) to ensure paging is working
    assertEquals(1, dataExportConfig.getElasticSearchPageSize());
    UUID docId = UUID.randomUUID();
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId.toString(), JsonApiDocuments.getMaterialSampleDocument(docId));
    UUID docId2 = UUID.randomUUID();
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId.toString(), JsonApiDocuments.getMaterialSampleDocument(docId2));

    // Do a query with no sort to ensure a default sort will be added for paging
    String query = "{\"query\": {\"match_all\": {}}}";

    DataExportDto dto =
      dataExportRepository.create(DataExportDto.builder()
        .source(MAT_SAMPLE_INDEX)
        .name("my export")
        .query(query)
        .columns(List.of("materialSampleName", "collectingEvent.dwcVerbatimLocality",
          "dwcCatalogNumber", "dwcOtherCatalogNumbers", "managedAttributes.attribute_1"))
        .build());
    assertNotNull(dto.getUuid());

    try {
      asyncConsumer.getAccepted().get(0).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    DataExportDto savedDataExportDto = dataExportRepository.findOne(dto.getUuid(), new QuerySpec(DataExportDto.class));
    assertEquals(DataExport.ExportStatus.COMPLETED, savedDataExportDto.getStatus());
    assertEquals(DataExport.ExportType.TABULAR_DATA, savedDataExportDto.getExportType());
    assertEquals("my export", savedDataExportDto.getName());

    ResponseEntity<InputStreamResource>
      response = fileController.downloadFile(dto.getUuid(), FileController.DownloadType.DATA_EXPORT);

    assertEquals("my_export.csv", response.getHeaders().getContentDisposition().getFilename());

    assertNotNull(response.getBody());
    String text = new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    List<String> lines = text.lines().toList();
    // make sure managedAttributes are extracted to specific column(s)
    assertTrue(lines.get(0).contains("managedAttributes.attribute_1"));

    // check that arrays are exported using ; as element separator
    assertTrue(lines.get(1).contains("cn1;cn1-1"));

    // delete the export
    dataExportRepository.delete(dto.getUuid());
    assertThrows(
      ResourceNotFoundException.class, () -> dataExportRepository.findOne(dto.getUuid(), new QuerySpec(DataExportDto.class)));
  }

}

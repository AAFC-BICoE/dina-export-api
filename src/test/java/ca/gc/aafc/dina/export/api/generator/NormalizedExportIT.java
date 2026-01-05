package ca.gc.aafc.dina.export.api.generator;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.ElasticSearchTestContainerInitializer;
import ca.gc.aafc.dina.export.api.async.AsyncConsumer;
import ca.gc.aafc.dina.export.api.dto.DataExportDto;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.file.FileController;
import ca.gc.aafc.dina.export.api.repository.DataExportRepository;
import ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for normalized export feature that creates separate samples.csv and projects.csv files.
 */
@ContextConfiguration(initializers = {ElasticSearchTestContainerInitializer.class})
public class NormalizedExportIT extends BaseIntegrationTest {

  private static final String MAT_SAMPLE_INDEX = "dina_material_sample_index";

  @Inject
  private DataExportRepository dataExportRepository;

  @Inject
  private DataExportServiceTransactionWrapper dataExportServiceTransactionWrapper;

  @Inject
  private FileController fileController;

  @Inject
  private ElasticsearchClient esClient;

  @Inject
  private AsyncConsumer<Future<UUID>> asyncConsumer;

  @BeforeEach
  public void setupElasticSearch() throws IOException {
    // Delete index if it exists from previous test
    try {
      esClient.indices().delete(d -> d.index(MAT_SAMPLE_INDEX));
    } catch (Exception e) {
      // Ignore if index doesn't exist
    }
  }

  @AfterEach
  public void cleanupElasticSearch() throws IOException {
    try {
      esClient.indices().delete(d -> d.index(MAT_SAMPLE_INDEX));
    } catch (Exception e) {
      // Ignore cleanup errors
    }
  }

  @Test
  public void testNormalizedExportWithProjectFilter()
      throws IOException, ExecutionException, InterruptedException,
             ca.gc.aafc.dina.exception.ResourceNotFoundException,
             ca.gc.aafc.dina.exception.ResourceGoneException {

    // Setup ElasticSearch index with test data
    ElasticSearchTestUtils.createIndex(esClient, MAT_SAMPLE_INDEX,
        "elasticsearch/material_sample_index_settings.json");

    // Create two projects
    UUID projectId1 = UUID.fromString("019b6a47-f755-7b2b-a3c1-79327203e928");
    UUID projectId2 = UUID.randomUUID();

    // Add test documents - 2 samples with project1, 1 sample with project2
    UUID docId1 = UUID.randomUUID();
    String testDoc1 = buildTestDocumentWithProject(docId1, "sample-001", projectId1, "Test Project 1");
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId1.toString(), testDoc1);

    UUID docId2 = UUID.randomUUID();
    String testDoc2 = buildTestDocumentWithProject(docId2, "sample-002", projectId1, "Test Project 1");
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId2.toString(), testDoc2);

    UUID docId3 = UUID.randomUUID();
    String testDoc3 = buildTestDocumentWithProject(docId3, "sample-003", projectId2, "Test Project 2");
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId3.toString(), testDoc3);

    // Wait a bit for documents to be indexed (no refreshIndex method available)
    Thread.sleep(1000);

    // Create normalized export filtering by projectId1
    UUID exportUuid = UUID.randomUUID();

    DataExport dataExport = DataExport.builder()
        .uuid(exportUuid)
        .name("Normalized Export Test")
        .createdBy("test-user")
        .source(MAT_SAMPLE_INDEX)
        .exportType(DataExport.ExportType.TABULAR_DATA)
        .query(Map.of("query", Map.of("nested", Map.of(
            "path", "included",
            "query", Map.of("bool", Map.of("must", List.of(
                Map.of("term", Map.of("included.id", projectId1.toString())),
                Map.of("term", Map.of("included.type", "project"))
            )))
        ))))
        .columns(new String[]{
            "materialSampleName",
            "projects.name",
            "projects.status",
            "projects.startDate"
        })
        .exportOptions(Map.of(
            "normalizeRelationships", "true",
            "columnSeparator", "COMMA"
        ))
        .build();

    dataExportServiceTransactionWrapper.createEntity(dataExport);

    // Wait for async processing to complete - get the last added task
    int taskIndex = asyncConsumer.getAccepted().size() - 1;
    asyncConsumer.getAccepted().get(taskIndex).get();

    // Verify export completed successfully
    DataExportDto savedDataExportDto = dataExportRepository.getOne(exportUuid, null).getDto();
    assertEquals(DataExport.ExportStatus.COMPLETED, savedDataExportDto.getStatus());

    // Download and verify the ZIP file
    ResponseEntity<InputStreamResource> response =
        fileController.downloadFile(exportUuid, FileController.DownloadType.DATA_EXPORT);

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());

    // Extract and verify ZIP contents
    try (ZipInputStream zis = new ZipInputStream(response.getBody().getInputStream())) {
      List<String> samplesLines = null;
      List<String> projectsLines = null;

      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String fileName = entry.getName();
        List<String> lines = readLinesFromZipEntry(zis);

        if ("samples.csv".equals(fileName)) {
          samplesLines = lines;
        } else if ("projects.csv".equals(fileName)) {
          projectsLines = lines;
        }

        zis.closeEntry();
      }

      // Verify samples.csv
      assertNotNull(samplesLines, "samples.csv should exist");
      assertTrue(samplesLines.size() >= 2, "Should have header + at least 1 sample row");
      
      // Print samples for debugging
      System.out.println("=== samples.csv ===");
      samplesLines.forEach(System.out::println);

      // Verify header contains expected columns
      String samplesHeader = samplesLines.get(0);
      assertTrue(samplesHeader.contains("\"Sample ID\"") || samplesHeader.contains("\"id\"") || samplesHeader.contains("materialSampleName"), 
          "Header should contain ID column");
      assertTrue(samplesHeader.contains("materialSampleName"), 
          "Header should contain materialSampleName");

      // Verify we only got samples from project1 (2 samples)
      assertEquals(3, samplesLines.size(), "Should have header + 2 sample rows (filtered by project)");

      // Verify projects.csv
      assertNotNull(projectsLines, "projects.csv should exist");
      
      // Print projects for debugging
      System.out.println("\n=== projects.csv ===");
      projectsLines.forEach(System.out::println);

      assertTrue(projectsLines.size() >= 2, "Should have header + at least 1 project row");
      
      // Verify project header
      String projectsHeader = projectsLines.get(0);
      assertTrue(projectsHeader.contains("\"Project ID\"") || projectsHeader.contains("\"id\""), 
          "Header should contain Project ID column");
      assertTrue(projectsHeader.contains("projects.name") || projectsHeader.contains("name"), 
          "Header should contain project name column");

      // Verify only one unique project (projectId1) appears
      assertEquals(2, projectsLines.size(), "Should have header + 1 unique project row");
      
      // Verify the project data contains the correct project
      String projectDataRow = projectsLines.get(1);
      assertTrue(projectDataRow.contains(projectId1.toString()) || 
                 projectDataRow.contains("Test Project 1"),
          "Project row should contain project1 ID or name");
    }
  }

  /**
   * Builds a test material sample document with a project relationship.
   */
  private String buildTestDocumentWithProject(UUID sampleId, String sampleName, 
                                              UUID projectId, String projectName) {
    return """
    {
      "data": {
        "id": "%s",
        "type": "material-sample",
        "attributes": {
          "version": 1,
          "group": "test-group",
          "createdOn": "2025-12-29T13:00:00Z",
          "createdBy": "test-user",
          "materialSampleName": "%s"
        },
        "relationships": {
          "projects": {
            "data": [{
              "id": "%s",
              "type": "project"
            }]
          }
        }
      },
      "included": [
        {
          "id": "%s",
          "type": "project",
          "attributes": {
            "createdOn": "2025-12-29T12:00:00Z",
            "createdBy": "test-user",
            "group": "test-group",
            "name": "%s",
            "startDate": "2025-01-01",
            "endDate": null,
            "status": "Active"
          }
        }
      ]
    }
    """.formatted(
        sampleId.toString(),
        sampleName,
        projectId.toString(),
        projectId.toString(),
        projectName
    );
  }

  /**
   * Helper method to read lines from a ZipEntry without closing the ZipInputStream.
   */
  private List<String> readLinesFromZipEntry(ZipInputStream zis) throws IOException {
    List<String> lines = new ArrayList<>();
    // Don't use try-with-resources here as it would close the underlying ZipInputStream
    BufferedReader reader = new BufferedReader(
        new InputStreamReader(zis, StandardCharsets.UTF_8));
    String line;
    while ((line = reader.readLine()) != null) {
      lines.add(line);
    }
    return lines;
  }
}

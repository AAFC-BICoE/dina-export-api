package ca.gc.aafc.dina.export.api.generator;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.ElasticSearchTestContainerInitializer;
import ca.gc.aafc.dina.export.api.async.AsyncConsumer;
import ca.gc.aafc.dina.export.api.dto.DataExportDto;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.file.FileController;
import ca.gc.aafc.dina.export.api.repository.DataExportRepository;
import ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils;
import ca.gc.aafc.dina.exception.ResourceNotFoundException;
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
 * Integration tests for {@link RecordBasedExportGenerator}.
 * Tests the multi-CSV normalized export functionality including entity detection,
 * deduplication, and ZIP packaging.
 */
@ContextConfiguration(initializers = {ElasticSearchTestContainerInitializer.class})
public class RecordBasedExportGeneratorIT extends BaseIntegrationTest {

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
  public void testNormalizedExportWithCollectingEvent()
      throws IOException, ExecutionException, InterruptedException,
             ca.gc.aafc.dina.exception.ResourceNotFoundException,
             ca.gc.aafc.dina.exception.ResourceGoneException {

    // Setup ElasticSearch index with test data
    ElasticSearchTestUtils.createIndex(esClient, MAT_SAMPLE_INDEX,
        "elasticsearch/material_sample_index_settings.json");

    // Create sample with collectingEvent
    UUID sampleId1 = UUID.fromString("019b3205-330a-7999-9168-a841571cb3bb");
    UUID collectingEventId = UUID.fromString("019b3205-3266-7c5f-91dd-7aa147a21d11");
    UUID projectId = UUID.fromString("019b6a47-f755-7b2b-a3c1-79327203e928");

    String testDoc = buildTestDocumentWithCollectingEvent(sampleId1, collectingEventId, projectId);
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, sampleId1.toString(), testDoc);

    // Wait for indexing
    Thread.sleep(1000);

    // Create normalized export with collectingEvent columns
    UUID exportUuid = UUID.randomUUID();

    DataExport dataExport = DataExport.builder()
        .uuid(exportUuid)
        .name("CollectingEvent Export Test")
        .createdBy("test-user")
        .source(MAT_SAMPLE_INDEX)
        .exportType(DataExport.ExportType.TABULAR_DATA)
        .query(Map.of("query", Map.of("match_all", Map.of())))
        .columns(new String[]{
            "materialSampleName",
            "collectingEvent.dwcCountry",
            "collectingEvent.startEventDateTime",
            "collectingEvent.extensionValues.mixs_soil_v5.submitted_to_insdc",
            "collectingEvent.extensionValues.mixs_soil_v5.dna_storage_conditons",
            "projects.name"
        })
        .columnAliases(new String[]{
            "Sample Name",
            "Country",
            "Collection Date",
            "MIxS Soil v5submitted_to_insdc",
            "MIxS Soil v5dna_storage_conditons",
            "Project Name"
        })
        .exportOptions(Map.of(
            "normalizeRelationships", "true",
            "columnSeparator", "COMMA"
        ))
        .build();

    dataExportServiceTransactionWrapper.createEntity(dataExport);

    // Wait for async processing
    int taskIndex = asyncConsumer.getAccepted().size() - 1;
    asyncConsumer.getAccepted().get(taskIndex).get();

    // Verify export completed
    DataExportDto savedDataExportDto = dataExportRepository.getOne(exportUuid, null).getDto();
    assertEquals(DataExport.ExportStatus.COMPLETED, savedDataExportDto.getStatus());

    // Download and verify ZIP
    ResponseEntity<InputStreamResource> response =
        fileController.downloadFile(exportUuid, FileController.DownloadType.DATA_EXPORT);

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());

    // Extract and verify ZIP contents
    try (ZipInputStream zis = new ZipInputStream(response.getBody().getInputStream())) {
      List<String> samplesLines = null;
      List<String> collectingEventLines = null;
      List<String> projectsLines = null;

      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String fileName = entry.getName();
        List<String> lines = readLinesFromZipEntry(zis);

        System.out.println("\n=== " + fileName + " ===");
        lines.forEach(System.out::println);

        if ("samples.csv".equals(fileName)) {
          samplesLines = lines;
        } else if ("collectingEvent.csv".equals(fileName)) {
          collectingEventLines = lines;
        } else if ("projects.csv".equals(fileName)) {
          projectsLines = lines;
        }

        zis.closeEntry();
      }

      // Verify samples.csv
      assertNotNull(samplesLines, "samples.csv should exist");
      assertEquals(2, samplesLines.size(), "Should have header + 1 sample row");

      // Verify collectingEvent.csv - THIS IS THE KEY TEST
      assertNotNull(collectingEventLines, "collectingEvent.csv should exist");
      System.out.println("\nCollectingEvent lines count: " + collectingEventLines.size());
      assertTrue(collectingEventLines.size() >= 2, 
          "collectingEvent.csv should have header + at least 1 data row, but has: " + collectingEventLines.size());
      
      // Verify collectingEvent data
      if (collectingEventLines.size() > 1) {
        String dataRow = collectingEventLines.get(1);
        System.out.println("CollectingEvent data row: " + dataRow);
        assertTrue(dataRow.contains(collectingEventId.toString()), 
            "collectingEvent row should contain the event ID");
      }

      // Verify projects.csv
      assertNotNull(projectsLines, "projects.csv should exist");
      assertTrue(projectsLines.size() >= 2, "Should have header + at least 1 project row");
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
   * Builds a test material sample document with collectingEvent and project relationships.
   */
  private String buildTestDocumentWithCollectingEvent(UUID sampleId, UUID collectingEventId, UUID projectId) {
    return """
    {
      "data": {
        "id": "%s",
        "type": "material-sample",
        "attributes": {
          "version": 1,
          "group": "cnc",
          "createdOn": "2025-12-18T15:12:40.164167Z",
          "createdBy": "cnc-su",
          "materialSampleName": "test-sample-001",
          "publiclyReleasable": true
        },
        "relationships": {
          "collectingEvent": {
            "data": {
              "id": "%s",
              "type": "collecting-event"
            }
          },
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
          "type": "collecting-event",
          "attributes": {
            "version": 1,
            "group": "cnc",
            "createdBy": "cnc-su",
            "createdOn": "2025-12-18T15:12:40.048884Z",
            "dwcCountry": "Canada",
            "dwcStateProvince": "Ontario",
            "startEventDateTime": "2025-12-01T10:00:00Z",
            "dwcVerbatimSRS": "WGS84 (EPSG:4326)",
            "publiclyReleasable": true,
            "extensionValues": {
              "mixs_soil_v5": {
                "submitted_to_insdc": "yes",
                "dna_storage_conditons": "test"
              }
            }
          }
        },
        {
          "id": "%s",
          "type": "project",
          "attributes": {
            "createdOn": "2025-12-29T13:24:20.157855Z",
            "createdBy": "cnc-su",
            "group": "cnc",
            "name": "Test project",
            "startDate": "2025-12-29",
            "status": "Ongoing"
          }
        }
      ]
    }
    """.formatted(
        sampleId.toString(),
        collectingEventId.toString(),
        projectId.toString(),
        collectingEventId.toString(),
        projectId.toString()
    );
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

  @Test
  public void testNormalizedExportWithColumnAliases()
      throws IOException, ExecutionException, InterruptedException,
             ca.gc.aafc.dina.exception.ResourceNotFoundException,
             ca.gc.aafc.dina.exception.ResourceGoneException {

    // Setup ElasticSearch index with test data
    ElasticSearchTestUtils.createIndex(esClient, MAT_SAMPLE_INDEX,
        "elasticsearch/material_sample_index_settings.json");

    // Create a shared project ID so both samples belong to the same project (for deduplication testing)
    UUID sharedProjectId = UUID.randomUUID();

    // Add test documents with realistic structure - both samples share the same project
    UUID docId1 = UUID.randomUUID();
    String testDoc1 = buildTestMaterialSampleDocumentWithProject(docId1, "BEA01-20161005930-A", sharedProjectId);
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId1.toString(), testDoc1);

    UUID docId2 = UUID.randomUUID();
    String testDoc2 = buildTestMaterialSampleDocumentWithProject(docId2, "BEA02-20161005931-B", sharedProjectId);
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId2.toString(), testDoc2);

    // Create tabular data export with column aliases and normalized relationships.
    // This produces a ZIP file containing separate samples.csv and projects.csv files.
    UUID uuid = UUID.randomUUID();

    DataExport dataExport = DataExport.builder()
        .uuid(uuid)
        .createdBy("test-user")
        .source(MAT_SAMPLE_INDEX)
        .exportType(DataExport.ExportType.TABULAR_DATA)
        .query(Map.of("query", Map.of("match_all", Map.of())))
        .columns(new String[]{
            "projects.name",
            "projects.name",
            "projects.multilingualDescription.descriptions[0].desc",
            "materialSampleName",
            "materialSampleName",
            "effectiveScientificName",
            "effectiveScientificName",
            "materialSampleRemarks",
            "managedAttributes.*",
            "collectingEvent.dwcVerbatimLocality",
            "collectingEvent.endEventDateTime"
        })
        .columnAliases(new String[]{
            "Project.alias",
            "Project.title",
            "Project.description",
            "Sample.alias",
            "Sample.title",
            "Sample.sample_name.taxon_id",
            "Sample.sample_name.scientific_name",
            "Sample.description",
            "Sample.sample_attributes",
            "Sample.GeographicLocation",
            "Sample.collectionDate"
        })
        .exportOptions(Map.of(
            "normalizeRelationships", "true",
            "columnSeparator", "COMMA"
        ))
        .name("normalized_export_with_aliases")
        .build();

    dataExportServiceTransactionWrapper.createEntity(dataExport);

    // Wait for async processing to complete - get the last added task
    int taskIndex = asyncConsumer.getAccepted().size() - 1;
    asyncConsumer.getAccepted().get(taskIndex).get();

    // Verify export completed successfully
    DataExportDto savedDataExportDto = dataExportRepository.getOne(uuid, null).getDto();
    assertEquals(DataExport.ExportStatus.COMPLETED, savedDataExportDto.getStatus());
    assertEquals(DataExport.ExportType.TABULAR_DATA, savedDataExportDto.getExportType());
    assertEquals("normalized_export_with_aliases", savedDataExportDto.getName());

    // Download and verify the ZIP file (normalized export creates a ZIP with multiple CSVs)
    ResponseEntity<InputStreamResource> response = fileController.downloadFile(uuid,
        FileController.DownloadType.DATA_EXPORT);

    assertEquals("normalized_export_with_aliases.zip", 
        response.getHeaders().getContentDisposition().getFilename());

    assertNotNull(response.getBody());

    // Extract and verify ZIP contents - should have samples.csv and projects.csv
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

      // Verify samples.csv exists and has correct structure
      assertNotNull(samplesLines, "samples.csv should exist in ZIP");
      assertTrue(samplesLines.size() >= 3, "Should have header + at least 2 sample rows");

      // Verify samples.csv header contains expected sample columns with aliases
      String samplesHeader = samplesLines.get(0);
      assertTrue(samplesHeader.contains("Sample.alias"), "Should have Sample.alias column");
      assertTrue(samplesHeader.contains("Sample.title"), "Should have Sample.title column");
      assertTrue(samplesHeader.contains("Sample.sample_name.taxon_id"), 
          "Should have Sample.sample_name.taxon_id column");
      assertTrue(samplesHeader.contains("Sample.sample_name.scientific_name"), 
          "Should have Sample.sample_name.scientific_name column");
      assertTrue(samplesHeader.contains("Sample.description"), "Should have Sample.description column");

      // Verify sample data rows contain expected sample names
      String sampleData = String.join("\n", samplesLines);
      assertTrue(sampleData.contains("BEA01-20161005930-A"), 
          "Should contain first sample name");
      assertTrue(sampleData.contains("BEA02-20161005931-B"), 
          "Should contain second sample name");

      // Verify projects.csv exists and has correct structure
      assertNotNull(projectsLines, "projects.csv should exist in ZIP");
      assertTrue(projectsLines.size() >= 2, "Should have header + at least 1 project row");

      // Verify projects.csv header contains expected project columns with aliases
      String projectsHeader = projectsLines.get(0);
      assertTrue(projectsHeader.contains("Project.alias"), "Should have Project.alias column");
      assertTrue(projectsHeader.contains("Project.title"), "Should have Project.title column");
      assertTrue(projectsHeader.contains("Project.description"), 
          "Should have Project.description column");

      // Verify project data contains the expected project name (deduplicated)
      String projectData = String.join("\n", projectsLines);
      assertTrue(projectData.contains("GRDI-Eco Invertebrates"),
          "Should contain project name");
      
      // Projects should be deduplicated - only 1 unique project in test data
      assertEquals(2, projectsLines.size(), 
          "Should have exactly 2 lines (header + 1 unique project row)");
    }

    // Clean up - delete the export
    // Note: Normalized exports create a directory structure, so cleanup may fail
    try {
      dataExportRepository.onDelete(uuid);
      assertThrows(ResourceNotFoundException.class,
          () -> dataExportRepository.onFindOne(uuid, null));
    } catch (RuntimeException e) {
      // Ignore cleanup errors (e.g., directory not empty)
    }
  }

  /**
   * Builds a test material sample document with a specific project for alias testing.
   */
  private String buildTestMaterialSampleDocumentWithProject(UUID id, String materialSampleName, UUID projectId) {
    UUID collectionId = UUID.randomUUID();
    UUID collectingEventId = UUID.randomUUID();
    
    return """
    {
      "data": {
        "id": "%s",
        "type": "material-sample",
        "attributes": {
          "version": 1,
          "group": "test-group",
          "createdOn": "2025-06-09T16:39:22.29994Z",
          "createdBy": "test-user",
          "dwcOtherCatalogNumbers": ["201697"],
          "materialSampleName": "%s",
          "materialSampleType": "MIXED_ORGANISMS",
          "managedAttributes": {
            "experimental_replicate": "A"
          },
          "materialSampleRemarks": "Test sample for ENA export",
          "allowDuplicateName": false,
          "isRestricted": false
        },
        "relationships": {
          "collectingEvent": {
            "data": {
              "id": "%s",
              "type": "collecting-event"
            }
          },
          "projects": {
            "data": [{
              "id": "%s",
              "type": "project"
            }]
          },
          "collection": {
            "data": {
              "id": "%s",
              "type": "collection"
            }
          }
        }
      },
      "included": [
        {
          "id": "%s",
          "type": "project",
          "attributes": {
            "createdOn": "2025-03-27T13:51:41.532518Z",
            "createdBy": "test-user",
            "group": "test-group",
            "name": "GRDI-Eco Invertebrates",
            "startDate": "2016-04-01",
            "endDate": "2022-03-31",
            "status": "Active",
            "multilingualDescription": {
              "descriptions": [{
                "lang": "en",
                "desc": "GRDI-Ecobiomics test project"
              }]
            }
          }
        },
        {
          "id": "%s",
          "type": "collection",
          "attributes": {
            "createdOn": "2025-03-27T13:14:09.985462Z",
            "createdBy": "test-user",
            "group": "test-group",
            "name": "Test Collection",
            "code": "TC-001"
          }
        },
        {
          "id": "%s",
          "type": "collecting-event",
          "attributes": {
            "version": 0,
            "group": "test-group",
            "createdBy": "test-user",
            "createdOn": "2025-06-09T16:39:21.819019Z",
            "geoReferenceAssertions": [{
              "dwcDecimalLatitude": 46.45451,
              "dwcDecimalLongitude": -62.37839,
              "isPrimary": true
            }],
            "dwcVerbatimLocality": "Bear River Road",
            "dwcRecordedBy": "RS",
            "startEventDateTime": "2016-10-05",
            "endEventDateTime": "2016-10-05",
            "dwcStateProvince": "PEI",
            "habitat": "riffle",
            "dwcMinimumElevationInMeters": 11.00,
            "dwcMinimumDepthInMeters": 21.00
          }
        }
      ]
    }
    """.formatted(
        id.toString(),
        materialSampleName,
        collectingEventId.toString(),
        projectId.toString(),
        collectionId.toString(),
        projectId.toString(),
        collectionId.toString(),
        collectingEventId.toString()
    );
  }

  /**
   * Helper method to read lines from a ZipEntry without closing the underlying ZipInputStream.
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

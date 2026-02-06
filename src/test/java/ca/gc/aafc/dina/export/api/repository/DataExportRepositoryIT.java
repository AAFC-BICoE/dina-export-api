package ca.gc.aafc.dina.export.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import ca.gc.aafc.dina.exception.ResourceGoneException;
import ca.gc.aafc.dina.exception.ResourceNotFoundException;
import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.ElasticSearchTestContainerInitializer;
import ca.gc.aafc.dina.export.api.async.AsyncConsumer;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.export.api.dto.DataExportDto;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.file.FileController;
import ca.gc.aafc.dina.export.api.testsupport.jsonapi.JsonApiDocuments;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import ca.gc.aafc.dina.repository.JsonApiModelAssistant;
import ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils;
import ca.gc.aafc.dina.testsupport.jsonapi.JsonAPITestHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
  public void testESDatasource()
    throws IOException, ResourceGoneException, ca.gc.aafc.dina.exception.ResourceNotFoundException {

    ElasticSearchTestUtils.createIndex(esClient, MAT_SAMPLE_INDEX, "elasticsearch/material_sample_index_settings.json");

    // Add 2 documents with  page size of 1 (from application-test.yml) to ensure paging is working
    assertEquals(1, dataExportConfig.getElasticSearchPageSize());
    UUID docId = UUID.randomUUID();
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId.toString(), JsonApiDocuments.getMaterialSampleDocument(docId));
    UUID docId2 = UUID.randomUUID();
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId2.toString(), JsonApiDocuments.getMaterialSampleDocument(docId2));

    // Do a query with no sort to ensure a default sort will be added for paging
    String query = "{\"query\": {\"match_all\": {}}}";

    // Use LinkedHashMap to preserve order - first entry is primary entity
    Map<String, List<String>> schema = new java.util.LinkedHashMap<>();
    schema.put("materialSample", List.of("id", "materialSampleName", "dwcCatalogNumber",
      "dwcOtherCatalogNumbers", "managedAttributes.attribute_1", "projects.name", "latLong", "concatResult"));
    schema.put("collectingEvent", List.of("dwcVerbatimLocality", "managedAttributes.attribute_ce_1"));

    DataExportDto dto = DataExportDto.builder()
      .source(MAT_SAMPLE_INDEX)
      .name("my export")
      .query(query)
      .schema(schema)
      .functions(Map.of("latLong",
        new DataExportFunction(DataExportFunction.FunctionDef.CONVERT_COORDINATES_DD,
          Map.of( DataExportFunction.CONVERT_COORDINATES_DD_PARAM, "collectingEvent.eventGeom")),
        "concatResult",
        new DataExportFunction(DataExportFunction.FunctionDef.CONCAT,
          Map.of( DataExportFunction.CONCAT_PARAM_ITEMS, List.of("materialSampleName", "const1"),
            DataExportFunction.CONCAT_PARAM_CONSTANTS, Map.of("const1", "!!!"),
            DataExportFunction.CONCAT_PARAM_SEPARATOR, "-")))
      )
      .build();

    JsonApiDocument docToCreate = ca.gc.aafc.dina.jsonapi.JsonApiDocuments.createJsonApiDocument(
      null, DataExportDto.TYPENAME,
      JsonAPITestHelper.toAttributeMap(dto)
    );

    var created = dataExportRepository.onCreate(docToCreate);
    UUID uuid =  JsonApiModelAssistant.extractUUIDFromRepresentationModelLink(created);
    assertNotNull(uuid);

    try {
      asyncConsumer.getAccepted().getFirst().get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    DataExportDto savedDataExportDto = dataExportRepository.getOne(uuid, null).getDto();
    assertEquals(DataExport.ExportStatus.COMPLETED, savedDataExportDto.getStatus());
    assertEquals(DataExport.ExportType.TABULAR_DATA, savedDataExportDto.getExportType());
    assertEquals("my export", savedDataExportDto.getName());

    ResponseEntity<InputStreamResource>
      response = fileController.downloadFile(uuid, FileController.DownloadType.DATA_EXPORT);

    assertEquals("my_export.csv", response.getHeaders().getContentDisposition().getFilename());

    assertNotNull(response.getBody());
    String text = new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    List<String> lines = text.lines().toList();

    // make sure id is exported, skip the header line and check if it's in 1 of the 2 exported line (order is undefined)
    assertTrue(lines.get(1).contains(docId.toString()) || lines.get(2).contains(docId.toString()));

    // make sure managedAttributes are extracted to specific column(s)
    assertTrue(lines.get(0).contains("managedAttributes.attribute_1"));

    // Check that values are exported
    var line1 = lines.get(1);

    // from relationship/included
    assertTrue(line1.contains("Montreal"));

    assertTrue(line1.contains("value ce 1"));

    // check that arrays are exported using ; as element separator
    assertTrue(line1.contains("cn1;cn1-1"));

    // check that to-many relationships are exported in a similar way of arrays
    assertTrue(line1.contains("project 1;project 2"));

    // Check convert dd function result
    assertTrue(line1.contains("45.424721,-75.695000"));

    // Check concat function result
    assertTrue(line1.contains("Yves-!!!"));

    // delete the export
    dataExportRepository.onDelete(uuid);
    assertThrows(
      ResourceNotFoundException.class, () -> dataExportRepository.onFindOne(uuid, null));
  }

  @Test
  public void testMultiEntityExport()
    throws IOException, ResourceGoneException, ca.gc.aafc.dina.exception.ResourceNotFoundException {

    ElasticSearchTestUtils.createIndex(esClient, MAT_SAMPLE_INDEX, "elasticsearch/material_sample_index_settings.json");

    // Add 2 documents
    UUID docId = UUID.randomUUID();
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId.toString(), JsonApiDocuments.getMaterialSampleDocument(docId));
    UUID docId2 = UUID.randomUUID();
    ElasticSearchTestUtils.indexDocument(esClient, MAT_SAMPLE_INDEX, docId2.toString(), JsonApiDocuments.getMaterialSampleDocument(docId2));

    String query = "{\"query\": {\"match_all\": {}}}";

    // Use LinkedHashMap to preserve order - first entry is primary entity
    Map<String, List<String>> schema = new java.util.LinkedHashMap<>();
    schema.put("materialSample", List.of("id", "materialSampleName", "dwcCatalogNumber"));
    schema.put("collectingEvent", List.of("dwcVerbatimLocality", "managedAttributes.attribute_ce_1"));

    DataExportDto dto = DataExportDto.builder()
      .source(MAT_SAMPLE_INDEX)
      .name("multi export")
      .query(query)
      .schema(schema)
      .exportOptions(Map.of(
        "enablePackaging", "true",  // Enable multi-entity ZIP export
        "enableIdTracking", "true"   // Track IDs to prevent duplicates across entity files
      ))
      .build();

    JsonApiDocument docToCreate = ca.gc.aafc.dina.jsonapi.JsonApiDocuments.createJsonApiDocument(
      null, DataExportDto.TYPENAME,
      JsonAPITestHelper.toAttributeMap(dto)
    );

    var created = dataExportRepository.onCreate(docToCreate);
    UUID uuid = JsonApiModelAssistant.extractUUIDFromRepresentationModelLink(created);
    assertNotNull(uuid);

    try {
      asyncConsumer.getAccepted().getFirst().get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    DataExportDto savedDataExportDto = dataExportRepository.getOne(uuid, null).getDto();
    assertEquals(DataExport.ExportStatus.COMPLETED, savedDataExportDto.getStatus());

    ResponseEntity<InputStreamResource> response = 
      fileController.downloadFile(uuid, FileController.DownloadType.DATA_EXPORT);

    // Should be a ZIP file
    String filename = response.getHeaders().getContentDisposition().getFilename();
    assertNotNull(filename);
    assertTrue(filename.endsWith(".zip"), "Expected ZIP file but got: " + filename);

    // Extract and validate ZIP contents
    assertNotNull(response.getBody());
    Map<String, List<String>> fileContents = new HashMap<>();
    
    try (ZipInputStream zis = new ZipInputStream(response.getBody().getInputStream())) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        List<String> lines = reader.lines().toList();
        fileContents.put(entry.getName(), lines);
        zis.closeEntry();
      }
    }

    // Verify we have both CSV files
    assertTrue(fileContents.containsKey("materialSample.csv"), "Missing materialSample.csv");
    assertTrue(fileContents.containsKey("collectingEvent.csv"), "Missing collectingEvent.csv");

    // Verify materialSample.csv content
    List<String> materialSampleLines = fileContents.get("materialSample.csv");
    assertTrue(materialSampleLines.size() >= 3, "Expected at least 3 lines in materialSample.csv (header + 2 data rows)");
    assertTrue(materialSampleLines.get(0).contains("id"));
    assertTrue(materialSampleLines.get(0).contains("materialSampleName"));
    assertTrue(materialSampleLines.get(1).contains(docId.toString()) || materialSampleLines.get(2).contains(docId.toString()));

    // Verify collectingEvent.csv content
    // Note: Both material samples reference the SAME collecting event, so with enableIdTracking
    // it should only appear once
    List<String> collectingEventLines = fileContents.get("collectingEvent.csv");
    assertTrue(collectingEventLines.size() >= 2, "Expected at least 2 lines in collectingEvent.csv (header + 1 data row)");
    assertTrue(collectingEventLines.get(0).contains("dwcVerbatimLocality"));
    assertTrue(collectingEventLines.get(1).contains("Montreal") || collectingEventLines.get(2).contains("Montreal"));

    // delete the export
    dataExportRepository.onDelete(uuid);
    assertThrows(
      ResourceNotFoundException.class, () -> dataExportRepository.onFindOne(uuid, null));
  }

}

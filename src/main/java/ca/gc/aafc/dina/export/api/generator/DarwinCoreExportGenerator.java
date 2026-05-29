package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;

import ca.gc.aafc.dina.export.api.config.DarwinCoreExportConfig;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.entity.DataExportSchemaEntry;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreContextBuilder;
import ca.gc.aafc.dina.export.api.generator.helper.DarwinCoreMapper;
import ca.gc.aafc.dina.export.api.generator.helper.RelationshipFlattener;
import ca.gc.aafc.dina.export.api.output.DataOutput;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;
import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;
import ca.gc.aafc.dina.messaging.producer.DinaMessageProducer;

import com.fasterxml.jackson.core.type.TypeReference;

import ca.gc.aafc.dina.export.api.output.TabularOutput;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ca.gc.aafc.dina.export.api.output.ZipPackager;

@Service
public class DarwinCoreExportGenerator extends RecordBasedExportGenerator {

  private static final TypeReference<Map<String, String>> MAP_STRING_TYPEREF = new TypeReference<>() { };

  private final ObjectMapper objectMapper;
  private final DarwinCoreExportConfig darwinCoreConfig;
  private final DarwinCoreContextBuilder contextBuilder;
  private final DarwinCoreMapper darwinCoreMapper;
  private final DarwinCoreMetaXmlGenerator metaXmlGenerator;

  // Opened in preRecordWrite, written by processEntity, closed in postRecordWrite
  private FileWriter csvWriter;
  private TabularOutput<UUID, Map<String, String>> csvOutput;

  public DarwinCoreExportGenerator(
    DataExportStatusService dataExportStatusService,
    DataExportConfig dataExportConfig,
    Configuration jsonPathConfiguration,
    ElasticSearchDataSource elasticSearchDataSource,
    ObjectMapper objectMapper,
    DinaMessageProducer messageProducer,
    DarwinCoreExportConfig darwinCoreConfig,
    DarwinCoreContextBuilder contextBuilder,
    DarwinCoreMapper darwinCoreMapper,
    DarwinCoreMetaXmlGenerator metaXmlGenerator
  ) {

    super(
      dataExportStatusService,
      dataExportConfig,
      jsonPathConfiguration,
      elasticSearchDataSource,
      objectMapper,
      messageProducer);

    this.objectMapper = objectMapper;
    this.darwinCoreConfig = darwinCoreConfig;
    this.contextBuilder = contextBuilder;
    this.darwinCoreMapper = darwinCoreMapper;
    this.metaXmlGenerator = metaXmlGenerator;
  }

  @Override
  public String generateFilename(DataExport dinaExport) {
    return "occurrence.zip";
  }

  @Override
  protected void preRecordWrite(DataExport dinaExport, Path exportPath) throws IOException {
    exportWorkDir = Files.createTempDirectory("dwc-archive-");
    Path csvPath = exportWorkDir.resolve(darwinCoreConfig.getCore().getFileLocations().getFirst());
    List<String> headers = darwinCoreConfig.getCore().getColumns().stream()
        .map(DarwinCoreExportConfig.ColumnMapping::getDwcTerm)
        .toList();
    csvWriter = new FileWriter(csvPath.toFile(), StandardCharsets.UTF_8);
    csvOutput = TabularOutput.create(
        TabularOutput.TabularOutputArgs.builder()
            .headers(headers)
            .columnSeparator(TabularOutput.ColumnSeparator.COMMA)
            .build(),
        MAP_STRING_TYPEREF, csvWriter);
  }

  @Override
  protected void exportSingleEntity(DataExport dinaExport,
                                    LinkedHashMap<String, DataExportSchemaEntry> schema,
                                    Path exportPath) throws IOException {
    queryAndProcess(dinaExport, null, false);
  }

  @Override
  protected void postRecordWrite(DataExport dinaExport, Path exportPath) throws IOException {
    if (csvOutput != null) {
      csvOutput.close();
      csvOutput = null;
    }
    if (csvWriter != null) {
      csvWriter.close();
      csvWriter = null;
    }
    metaXmlGenerator.generateMetaXml(exportWorkDir.resolve("meta.xml"));
    super.postRecordWrite(dinaExport, exportPath);
  }

  @Override
  public void deleteExport(DataExport dinaExport) throws IOException {
    if (dinaExport.getExportType() != DataExport.ExportType.DWCA) {
      throw new IllegalArgumentException("Should only be used for ExportType DWCA");
    }
    doDeleteExport(dinaExport);
  }

  /**
   * Override processEntity to build DwC records from JSON:API entities.
   * Ignores the tabular output parameter; writes directly to {@code csvOutput}.
   */
  @Override
  protected void processEntity(JsonNode entity, String fallbackId,
                               JsonNode relationshipSource,
                               Map<String, DataExportFunction> functions,
                               DataOutput<UUID, JsonNode> output) throws IOException {
    if (entity == null) {
      return;
    }

    // Flatten to attributes + embedded included relationships before context navigation
    var denormalized = RelationshipFlattener.toFlatEntity(objectMapper, entity, relationshipSource);

    String entityId = extractText(denormalized, JSONApiDocumentStructure.ID, fallbackId);
    if (StringUtils.isBlank(entityId)) {
      return;
    }

    Map<String, JsonNode> entitiesContext = contextBuilder.buildContextMap(denormalized);
    Map<String, String> dwcRecord = buildDwcRecord(entitiesContext);

    if (csvOutput != null) {
      csvOutput.addRecord(UUID.fromString(entityId), dwcRecord);
    }
  }

  /**
   * Builds a complete DarwinCore Archive ZIP from a list of pre-fetched entities.
   * Package-private for test use; production exports go through {@link #export(DataExport)}.
   *
   * @param entities   denormalized JSON:API entities
   * @param outputPath path for the output ZIP file
   */
  void generateArchive(List<JsonNode> entities, Path outputPath) throws IOException {
    preRecordWrite(null, outputPath);
    try {
      for (JsonNode entity : entities) {
        processEntity(entity, null, null, null, null);
      }
    } catch (IOException e) {
      // clean up on failure; postRecordWrite won't be reached
      if (exportWorkDir != null) {
        ZipPackager.deleteDirectoryRecursively(exportWorkDir);
        exportWorkDir = null;
      }
      csvOutput = null;
      if (csvWriter != null) {
        csvWriter.close();
        csvWriter = null;
      }
      throw e;
    }
    postRecordWrite(null, outputPath);
  }

  private Map<String, String> buildDwcRecord(Map<String, JsonNode> entitiesContext) {
    Map<String, String> record = new LinkedHashMap<>();
    List<DarwinCoreExportConfig.ColumnMapping> columnMappings = darwinCoreConfig.getCore().getColumns();

    for (DarwinCoreExportConfig.ColumnMapping mapping : columnMappings) {
      String value = darwinCoreMapper.extractValue(entitiesContext, mapping);
      record.put(mapping.getDwcTerm(), value != null ? value : "");
    }

    return record;
  }
}

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

@Service
public class DarwinCoreExportGenerator extends RecordBasedExportGenerator {

  private static final TypeReference<Map<String, String>> MAP_STRING_TYPEREF = new TypeReference<>() { };

  private final ObjectMapper objectMapper;
  private final DarwinCoreExportConfig darwinCoreConfig;
  private final DarwinCoreContextBuilder contextBuilder;
  private final DarwinCoreMapper darwinCoreMapper;
  private final DarwinCoreMetaXmlGenerator metaXmlGenerator;

  private record DwcExportState(FileWriter csvWriter, TabularOutput<UUID, Map<String, String>> csvOutput) {}

  // Per-thread export state: populated by preRecordWrite, consumed by postRecordWrite.
  private final ThreadLocal<DwcExportState> exportState = new ThreadLocal<>();

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
    Path workDir = Files.createTempDirectory("dwc-archive-");
    exportWorkDir.set(workDir);
    Path csvPath = workDir.resolve(darwinCoreConfig.getCore().getFileLocations().getFirst());
    List<String> headers = darwinCoreConfig.getCore().getColumns().stream()
        .map(DarwinCoreExportConfig.ColumnMapping::getDwcTerm)
        .toList();
    FileWriter csvWriter = new FileWriter(csvPath.toFile(), StandardCharsets.UTF_8);
    TabularOutput<UUID, Map<String, String>> csvOutput = TabularOutput.create(
        TabularOutput.TabularOutputArgs.builder()
            .headers(headers)
            .columnSeparator(TabularOutput.ColumnSeparator.COMMA)
            .build(),
        MAP_STRING_TYPEREF, csvWriter);
    exportState.set(new DwcExportState(csvWriter, csvOutput));
  }

  @Override
  protected void exportSingleEntity(DataExport dinaExport,
                                    LinkedHashMap<String, DataExportSchemaEntry> schema,
                                    Path exportPath) throws IOException {
    queryAndProcess(dinaExport, null, false);
  }

  @Override
  protected void postRecordWrite(DataExport dinaExport, Path exportPath) throws IOException {
    DwcExportState state = exportState.get();
    if (state != null) {
      exportState.remove();
      try {
        state.csvOutput().close();
      } finally {
        state.csvWriter().close();
      }
    }
    metaXmlGenerator.generateMetaXml(exportWorkDir.get().resolve("meta.xml"));
    super.postRecordWrite(dinaExport, exportPath);
  }

  @Override
  public void deleteExport(DataExport dinaExport) throws IOException {
    if (dinaExport.getExportType() != DataExport.ExportType.DWCA) {
      throw new IllegalArgumentException("Should only be used for ExportType DWCA");
    }
    doDeleteExport(dinaExport);
  }

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

    DwcExportState state = exportState.get();
    if (state != null) {
      state.csvOutput().addRecord(UUID.fromString(entityId), dwcRecord);
    }
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

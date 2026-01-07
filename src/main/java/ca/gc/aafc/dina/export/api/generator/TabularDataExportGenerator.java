package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.collections4.MapUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.output.DataOutput;
import ca.gc.aafc.dina.export.api.output.TabularOutput;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;
import ca.gc.aafc.dina.export.api.source.ElasticSearchDataSource;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.log4j.Log4j2;

/**
 * Responsible to generate tabular export file.
 */
@Service
@Log4j2
public class TabularDataExportGenerator extends DataExportGenerator {

  private final ObjectMapper objectMapper;
  private final ElasticSearchDataSource elasticSearchDataSource;
  private final DataExportConfig dataExportConfig;
  private final RecordBasedExportHelper recordHelper;

  public TabularDataExportGenerator(
      DataExportStatusService dataExportStatusService,
      DataExportConfig dataExportConfig,
      ElasticSearchDataSource elasticSearchDataSource,
      ObjectMapper objectMapper,
      RecordBasedExportHelper recordHelper) {
    super(dataExportStatusService);
    this.elasticSearchDataSource = elasticSearchDataSource;
    this.objectMapper = objectMapper;
    this.dataExportConfig = dataExportConfig;
    this.recordHelper = recordHelper;
  }

  @Override
  public String generateFilename(DataExport dinaExport) {
    TabularOutput.TabularOutputArgs args = createTabularOutputArgsFrom(dinaExport);
    return DataExportConfig.DATA_EXPORT_TABULAR_FILENAME + switch (args.getColumnSeparator()) {
      case TAB -> ".tsv";
      case COMMA -> ".csv";
      case null -> ".csv";
    };
  }

  /**
   * Main export method for standard single CSV export.
   */
  @Async(DataExportConfig.DINA_THREAD_POOL_BEAN_NAME)
  public CompletableFuture<UUID> export(DataExport dinaExport) throws IOException {
    DataExport.ExportStatus currStatus = waitForRecord(dinaExport.getUuid());

    if (DataExport.ExportStatus.NEW != currStatus) {
      log.error("Unexpected DataExport status: {}", currStatus);
      return CompletableFuture.completedFuture(dinaExport.getUuid());
    }

    Path exportPath = dataExportConfig.getPathForDataExport(dinaExport).orElse(null);
    if (exportPath == null) {
      log.error("Null export path");
      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
      return CompletableFuture.completedFuture(dinaExport.getUuid());
    }

    updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.RUNNING);

    try {
      ensureDirectoryExists(exportPath.getParent());

      List<String> expandedColumns = new ArrayList<>(Arrays.asList(dinaExport.getColumns()));
      List<String> expandedAliases = dinaExport.getColumnAliases() != null ?
          new ArrayList<>(Arrays.asList(dinaExport.getColumnAliases())) : null;

      recordHelper.expandWildcardsIfNeeded(dinaExport, expandedColumns, expandedAliases);
      exportStandard(dinaExport, exportPath, expandedColumns, expandedAliases);

      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.COMPLETED);
    } catch (IOException ioEx) {
      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
      throw ioEx;
    }

    return CompletableFuture.completedFuture(dinaExport.getUuid());
  }

  /**
   * Standard single CSV export.
   */
  private void exportStandard(DataExport dinaExport, Path exportPath,
                               List<String> expandedColumns, List<String> expandedAliases) throws IOException {
    DataExport expandedExport = DataExport.builder()
        .uuid(dinaExport.getUuid())
        .columns(expandedColumns.toArray(new String[0]))
        .columnAliases(expandedAliases != null ? expandedAliases.toArray(new String[0]) : null)
        .exportOptions(dinaExport.getExportOptions())
        .build();

    try (Writer w = new FileWriter(exportPath.toFile(), StandardCharsets.UTF_8);
         TabularOutput<JsonNode> output = TabularOutput.create(
             createTabularOutputArgsFrom(expandedExport), new TypeReference<>() { }, w)) {
      
      paginateThroughResults(dinaExport.getSource(), 
          objectMapper.writeValueAsString(dinaExport.getQuery()),
          hit -> processRecord(hit.id(), hit.source(), dinaExport.getFunctions(), output));
    }
  }

  @Override
  public void deleteExport(DataExport dinaExport) throws IOException {
    if (dinaExport.getExportType() != DataExport.ExportType.TABULAR_DATA) {
      throw new IllegalArgumentException("Should only be used for ExportType TABULAR_DATA");
    }

    Path exportPath = dataExportConfig.getPathForDataExport(dinaExport).orElse(null);
    deleteIfExists(exportPath);

    if (exportPath != null &&
        DataExportConfig.isExportTypeUsesDirectory(DataExport.ExportType.TABULAR_DATA) &&
        DataExportConfig.isDataExportDirectory(exportPath.getParent(), dinaExport)) {
      deleteIfExists(exportPath.getParent());
    }
  }

  // ========== Helper Methods ==========

  /**
   * Paginates through Elasticsearch results using PIT (Point in Time).
   */
  private void paginateThroughResults(String sourceIndex, String query, 
                                       RecordProcessor processor) throws IOException {
    SearchResponse<JsonNode> response = elasticSearchDataSource.searchWithPIT(sourceIndex, query);

    boolean pageAvailable = !response.hits().hits().isEmpty();
    while (pageAvailable) {
      for (Hit<JsonNode> hit : response.hits().hits()) {
        processor.process(hit);
      }
      pageAvailable = false;

      int numberOfHits = response.hits().hits().size();
      if (elasticSearchDataSource.getPageSize() == numberOfHits) {
        Hit<JsonNode> lastHit = response.hits().hits().get(numberOfHits - 1);
        response = elasticSearchDataSource.searchAfter(query, response.pitId(), lastHit.sort());
        pageAvailable = true;
      }
    }

    elasticSearchDataSource.closePointInTime(response.pitId());
  }

  @FunctionalInterface
  private interface RecordProcessor {
    void process(Hit<JsonNode> hit) throws IOException;
  }

  /**
   * Processes a single record for standard export.
   */
  private void processRecord(String documentId, JsonNode record,
                              Map<String, DataExportFunction> columnFunctions,
                              DataOutput<JsonNode> output) {
    if (record == null) {
      return;
    }
    
    ObjectNode attributeObjNode = recordHelper.prepareAttributeNode(documentId, record);
    if (attributeObjNode == null) {
      return;
    }

    recordHelper.applyFunctions(attributeObjNode, columnFunctions);
    
    try {
      output.addRecord("record", attributeObjNode);
    } catch (IOException e) {
      log.error("Error writing record", e);
    }
  }

  private static TabularOutput.TabularOutputArgs createTabularOutputArgsFrom(DataExport dinaExport) {
    List<String> headerAliases = dinaExport.getColumnAliases() != null ?
        Arrays.asList(dinaExport.getColumnAliases()) : null;

    var builder = TabularOutput.TabularOutputArgs.builder()
        .headers(Arrays.asList(dinaExport.getColumns()))
        .receivedHeadersAliases(headerAliases);

    if (MapUtils.isNotEmpty(dinaExport.getExportOptions())) {
      String columnSeparator = dinaExport.getExportOptions().get(TabularOutput.OPTION_COLUMN_SEPARATOR);
      if (columnSeparator != null) {
        TabularOutput.ColumnSeparator.fromString(columnSeparator)
            .filter(s -> s != TabularOutput.ColumnSeparator.COMMA)
            .ifPresent(builder::columnSeparator);
      }
    }
    return builder.build();
  }
}

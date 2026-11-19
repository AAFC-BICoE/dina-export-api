package ca.gc.aafc.dina.export.api.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import jakarta.persistence.NoResultException;
import lombok.extern.log4j.Log4j2;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;

/**
 * Main abstract class for data export generator.
 * Also responsible to handle export status.
 */
@Log4j2
public abstract class DataExportGenerator {

  private static final int MAX_ATTEMPTS = 5;
  private static final long RETRY_INTERVAL = 500;

  private final DataExportStatusService dataExportStatusService;

  protected DataExportGenerator(DataExportStatusService dataExportStatusService) {
    this.dataExportStatusService = dataExportStatusService;
  }

  /**
   * Generates a filename (including extension) based on the dataExport
   * @param dataExport
   * @return
   */
  public abstract String generateFilename(DataExport dataExport);

  /**
   * The implementation should use Async annotation
   * @param dinaExport
   * @return
   */
  public abstract CompletableFuture<UUID> export(DataExport dinaExport) throws IOException;

  public abstract void deleteExport(DataExport dinaExport) throws IOException;

  protected void ensureDirectoryExists(Path directory) throws IOException {
    if (directory != null) {
      Files.createDirectories(directory);
    }
  }

  protected void deleteIfExists(Path filePath) throws IOException {

    if (filePath == null) {
      log.warn("Export file path is null, ignoring");
      return;
    }

    if (filePath.toFile().exists()) {
      Files.delete(filePath);
    } else {
      log.warn("Export {} file could not be deleted, not found", filePath);
    }
  }

  protected void updateStatus(UUID uuid, DataExport.ExportStatus status) {
    dataExportStatusService.updateStatus(uuid, status);
  }

  /**
   * Wait for the DataExport record to exist and return its status.
   * @param uuid
   * @return
   */
  protected DataExport.ExportStatus waitForRecord(UUID uuid) {
    RetryPolicy retryPolicy = RetryPolicy.builder()
      .maxRetries(MAX_ATTEMPTS - 1)
      .delay(Duration.ofMillis(RETRY_INTERVAL))
      .includes(NoResultException.class)
      .build();
    RetryTemplate template = new RetryTemplate(retryPolicy);
    return template.invoke(() -> dataExportStatusService.findStatus(uuid));
  }
}

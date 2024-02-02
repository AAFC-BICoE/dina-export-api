package ca.gc.aafc.dina.export.api.generator;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.persistence.NoResultException;

import org.springframework.retry.support.RetryTemplate;

import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;

/**
 * Main abstract class for data export generator.
 * Also responsible to handle export status.
 */
public abstract class DataExportGenerator {

  private static final int MAX_RETRY = 5;
  private static final long RETRY_INTERVAL = 500;

  private final DataExportStatusService dataExportStatusService;

  protected DataExportGenerator(DataExportStatusService dataExportStatusService) {
    this.dataExportStatusService = dataExportStatusService;
  }

  /**
   * The implementation should use Async annotation
   * @param dinaExport
   * @return
   */
  public abstract CompletableFuture<UUID> export(DataExport dinaExport) throws IOException;

  protected void updateStatus(UUID uuid, DataExport.ExportStatus status) {
    dataExportStatusService.updateStatus(uuid, status);
  }

  /**
   * Wait for the DataExport record to exist and return its status.
   * @param uuid
   * @return
   */
  protected DataExport.ExportStatus waitForRecord(UUID uuid) {
    RetryTemplate template = RetryTemplate.builder()
      .maxAttempts(MAX_RETRY)
      .fixedBackoff(RETRY_INTERVAL)
      .retryOn(NoResultException.class)
      .build();
    return template.execute(ctx -> dataExportStatusService.findStatus(uuid));
  }
}

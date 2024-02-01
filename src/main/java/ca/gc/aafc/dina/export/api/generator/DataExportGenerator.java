package ca.gc.aafc.dina.export.api.generator;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import ca.gc.aafc.dina.export.api.entity.DataExport;

/**
 * main interface for data export generator.
 */
public interface DataExportGenerator {

  /**
   * The implementation should use Async annotation
   * @param dinaExport
   * @return
   * @throws IOException
   */
  CompletableFuture<UUID> export(DataExport dinaExport) throws IOException;
}

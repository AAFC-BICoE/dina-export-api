package ca.gc.aafc.dina.export.api.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.file.FileController;
import ca.gc.aafc.dina.jpa.BaseDAO;

@Service
@Log4j2
public class ExpiredExportRemovalService {

  private static final int MAX_QUERY_LIMIT = 100;

  private final BaseDAO baseDAO;
  private final FileController fileController;
  private final Duration expiredExportMaxAge;
  private final DataExportService exportService;

  private static final String SELECT_BY_STATUS = "SELECT de " +
    "FROM " + DataExport.class.getCanonicalName() + " de WHERE status= :status";

  public ExpiredExportRemovalService(BaseDAO baseDAO,
                                     @Named("dataExportConfig") DataExportConfig dataExportConfig,
                                     FileController fileController,
                                     DataExportService exportService) {
    this.baseDAO = baseDAO;
    this.expiredExportMaxAge = dataExportConfig.getExpiredExportMaxAge();
    this.fileController = fileController;
    this.exportService = exportService;
  }

  @Scheduled(cron = "#{@dataExportConfig.expiredExportCronExpression}")
  @Transactional
  public void onCron() {

    // handle expired exports that should be removed
    handleRemoval();

    //handle export that are now expired
    handleExpired();
  }

  private void handleRemoval() {
    List<DataExport> expiredExport = baseDAO.findAllByQuery(
      DataExport.class, SELECT_BY_STATUS,
      List.of(Pair.of("status", DataExport.ExportStatus.EXPIRED)),
      MAX_QUERY_LIMIT, 0);

    for (DataExport de : expiredExport) {
      if (isReadyForRemoval(de)) {
        Optional<Path> exportFile = fileController.getExportFileLocation(de.getUuid(), de.getFilename());
        if (exportFile.isEmpty()) {
          exportService.delete(de, false);
          log.info("Expired DataExport {} deleted.", de.getUuid());
        } else {
          log.error("Expired DataExport {} still has a file. Won't be removed.", de.getUuid());
        }
      }
    }
  }

  private void handleExpired() {
    List<DataExport> completedExport = baseDAO.findAllByQuery(
      DataExport.class, SELECT_BY_STATUS,
      List.of(Pair.of("status", DataExport.ExportStatus.COMPLETED)),
      MAX_QUERY_LIMIT, 0);

    for (DataExport de : completedExport) {
      if (isExpired(de)) {
        Optional<Path> exportFile = fileController.getExportFileLocation(de.getUuid(), de.getFilename());
        if (exportFile.isPresent()) {
          try {
            Files.delete(exportFile.get());
            log.info("DataExport {} is now expired. File {} deleted ", de.getUuid(), exportFile.get());
          } catch (IOException e) {
            log.error(e);
          }
        } else {
          log.error("Could not find path for export {}", de.getUuid());
        }
        de.setStatus(DataExport.ExportStatus.EXPIRED);
        exportService.update(de);
      }
    }
  }

  private LocalDateTime getExpirationDateTime(LocalDateTime createdDate) {
    if (expiredExportMaxAge == null) {
      return createdDate.plusWeeks(1);
    }
    return createdDate.plusSeconds(expiredExportMaxAge.getSeconds());
  }

  /**
   * Checks if a {@link DataExport} is considered expired based on
   * configuration or default (1 week).
   * @param dataExport
   * @return
   */
  private boolean isExpired(DataExport dataExport) {
    LocalDateTime expiration = getExpirationDateTime(dataExport.getCreatedOn().toLocalDateTime());
    return LocalDateTime.now().isAfter(expiration);
  }

  private boolean isReadyForRemoval(DataExport dataExport) {
    LocalDateTime expiration = getExpirationDateTime(dataExport.getCreatedOn().toLocalDateTime());
    LocalDateTime removalDate = expiration.plusWeeks(1);
    return LocalDateTime.now().isAfter(removalDate);
  }

}

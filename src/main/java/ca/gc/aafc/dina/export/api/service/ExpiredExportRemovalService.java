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
  public void removeExpired() {

    String sql =
      "SELECT de " +
        "FROM " + DataExport.class.getCanonicalName() + " de " +
        "WHERE status= :status";

    List<DataExport> completedExport = baseDAO.findAllByQuery(
      DataExport.class, sql,
      List.of(Pair.of("status", DataExport.ExportStatus.COMPLETED)),
      MAX_QUERY_LIMIT, 0);

    for (DataExport de : completedExport) {
      if (isExpired(de)) {
        Optional<Path> exportFile = fileController.getExportFileLocation(de.getUuid());
        if (exportFile.isPresent()) {
          try {
            Files.delete(exportFile.get());
            log.info(" {} deleted ", exportFile.get());
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

  private boolean isExpired(DataExport dataExport) {
    LocalDateTime createdDate = dataExport.getCreatedOn().toLocalDateTime();
    LocalDateTime expiration;
    if (expiredExportMaxAge == null) {
      expiration = createdDate.plusWeeks(1);
    } else {
      expiration = createdDate.plusSeconds(expiredExportMaxAge.getSeconds());
    }
    return LocalDateTime.now().isAfter(expiration);
  }

}

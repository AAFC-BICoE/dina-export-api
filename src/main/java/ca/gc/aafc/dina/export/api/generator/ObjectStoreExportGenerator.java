package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.UserNotificationQueueProperties;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.file.FileDownloader;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;
import ca.gc.aafc.dina.messaging.message.UserMessageNotification;
import ca.gc.aafc.dina.messaging.producer.DinaMessageProducer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class ObjectStoreExportGenerator extends DataExportGenerator {

  private final FileDownloader fileDownloader;
  private final DataExportConfig dataExportConfig;
  private final DinaMessageProducer messageProducer;

  public ObjectStoreExportGenerator(DataExportConfig dataExportConfig,
                                    FileDownloader fileDownloader,
                                    DataExportStatusService dataExportStatusService,
                                    DinaMessageProducer messageProducer) {

    super(dataExportStatusService);

    this.fileDownloader = fileDownloader;
    this.dataExportConfig = dataExportConfig;
    this.messageProducer = messageProducer;
  }

  @Override
  public String generateFilename(DataExport dataExport) {
    return dataExport.getUuid().toString() + ".zip";
  }

  @Async(DataExportConfig.DINA_THREAD_POOL_BEAN_NAME)
  @Override
  public CompletableFuture<UUID> export(DataExport dinaExport) throws IOException {

    DataExport.ExportStatus currStatus = waitForRecord(dinaExport.getUuid());

    if (currStatus == DataExport.ExportStatus.NEW) {

      Path exportPath = dataExportConfig.getPathForDataExport(dinaExport).orElse(null);
      if (exportPath == null) {
        log.error("Null export path");
        updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
        return CompletableFuture.completedFuture(dinaExport.getUuid());
      }

      //Create the directory if it doesn't exist
      ensureDirectoryExists(exportPath.getParent());

      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.RUNNING);

      String downloadUrl = StringUtils.appendIfMissing(dataExportConfig.getObjectStoreDownloadUrl(), "/")
        + dinaExport.getTransitiveData().get(DataExportConfig.OBJECT_STORE_TOA);

      try {
        // call download
        fileDownloader.downloadFile(downloadUrl, filename -> exportPath);
        updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.COMPLETED);

        messageProducer.send(buildUserMessageNotification(dinaExport));
      } catch (IOException | IllegalStateException ex) {
        updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
        throw ex;
      }
    } else {
      log.error("Unexpected DataExport status: {}", currStatus);
    }

    return CompletableFuture.completedFuture(dinaExport.getUuid());
  }

  private UserMessageNotification buildUserMessageNotification(DataExport dinaExport) {
    return UserMessageNotification
      .builder()
      .username(dinaExport.getCreatedBy())
      .title("Object Export Ready")
      .notificationType(UserNotificationQueueProperties.NotificationType.OBJECT_EXPORT_READY.name())
      .notificationParams(Map.of("id", dinaExport.getUuid().toString()))
      .build();
  }

  @Override
  public void deleteExport(DataExport dinaExport) throws IOException {
    if (dinaExport.getExportType() != DataExport.ExportType.OBJECT_ARCHIVE) {
      throw new IllegalArgumentException("Should only be used for ExportType OBJECT_ARCHIVE");
    }

    Path exportPath = dataExportConfig.getPathForDataExport(dinaExport).orElse(null);
    deleteIfExists(exportPath);
  }
}

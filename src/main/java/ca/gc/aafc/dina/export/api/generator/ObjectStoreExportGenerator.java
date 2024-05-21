package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.file.FileDownloader;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class ObjectStoreExportGenerator extends DataExportGenerator {

  private final FileDownloader fileDownloader;
  private final DataExportConfig dataExportConfig;

  public ObjectStoreExportGenerator(DataExportConfig dataExportConfig,
                                    FileDownloader fileDownloader,
                                    DataExportStatusService dataExportStatusService) {

    super(dataExportStatusService);

    this.fileDownloader = fileDownloader;
    this.dataExportConfig = dataExportConfig;
  }

  @Async(DataExportConfig.DINA_THREAD_POOL_BEAN_NAME)
  @Override
  public CompletableFuture<UUID> export(DataExport dinaExport) throws IOException {

    DataExport.ExportStatus currStatus = waitForRecord(dinaExport.getUuid());

    if(currStatus == DataExport.ExportStatus.NEW) {

      Path exportPath = dataExportConfig.getPathForDataExport(dinaExport);
      if(exportPath == null || !Files.exists(exportPath)) {
        log.error("No export path could be found");
        updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
        return CompletableFuture.completedFuture(dinaExport.getUuid());
      }
      
      //Create the directory if it doesn't exist
      Files.createDirectories(exportPath.getParent());

      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.RUNNING);

      String downloadUrl = StringUtils.appendIfMissing(dataExportConfig.getObjectStoreDownloadUrl(), "/")
        + dinaExport.getTransitiveData().get(DataExportConfig.OBJECT_STORE_TOA);

      try {
        // call download
        fileDownloader.downloadFile(downloadUrl, filename -> exportPath);
        updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.COMPLETED);
      } catch (IOException | IllegalStateException ex) {
        updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
        throw ex;
      }
    } else {
      log.error("Unexpected DataExport status: {}", currStatus);
    }

    return CompletableFuture.completedFuture(dinaExport.getUuid());
  }

  @Override
  public void deleteExport(DataExport dinaExport) throws IOException {
    Path exportPath = dataExportConfig.getPathForDataExport(dinaExport);
    if (exportPath.toFile().exists()) {
      Files.delete(exportPath);
    } else {
      log.warn("export {} files could not be deleted, not found", dinaExport.getUuid());
    }
  }
}

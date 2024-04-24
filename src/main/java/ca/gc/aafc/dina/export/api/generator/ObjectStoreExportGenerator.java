package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.io.FilenameUtils;
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
  private final Path workingFolder;
  private final DataExportConfig dataExportConfig;

  public ObjectStoreExportGenerator(DataExportConfig dataExportConfig,
                                    FileDownloader fileDownloader,
                                    DataExportStatusService dataExportStatusService) {

    super(dataExportStatusService);

    this.fileDownloader = fileDownloader;
    this.dataExportConfig = dataExportConfig;
    this.workingFolder = dataExportConfig.getGeneratedDataExportsPath();
  }

  @Async(DataExportConfig.DINA_THREAD_POOL_BEAN_NAME)
  @Override
  public CompletableFuture<UUID> export(DataExport dinaExport) throws IOException {

    // make sure the destination folder exists
    if(!workingFolder.toFile().exists()) {
      Files.createDirectories(workingFolder);
    }

    DataExport.ExportStatus currStatus = waitForRecord(dinaExport.getUuid());

    if(currStatus == DataExport.ExportStatus.NEW) {

      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.RUNNING);

      String downLoadUrl = StringUtils.appendIfMissing(dataExportConfig.getObjectStoreDownloadUrl(), "/")
        + dinaExport.getTransitiveData().get(DataExportConfig.OBJECT_STORE_TOA);

      try {
        // call download
        fileDownloader.downloadFile(downLoadUrl,
          (filename) -> generatePath(dinaExport.getUuid(), filename));
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

  private Path generatePath(UUID exportUuid, String downloadFilename) {
    String ext = "";
    if (StringUtils.isNotBlank(downloadFilename)) {
      ext = FilenameUtils.getExtension(downloadFilename);
    }
    return workingFolder.resolve(exportUuid.toString() + "." + StringUtils.defaultIfBlank(ext, "tmp"));
  }

}

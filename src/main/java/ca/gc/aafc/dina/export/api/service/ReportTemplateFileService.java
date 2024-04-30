package ca.gc.aafc.dina.export.api.service;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.ReportTemplateConfig;
import ca.gc.aafc.dina.export.api.file.FileDownloader;

/**
 * Responsible for the file on disk of the template represented by a ReportTemplate.
 */
@Service
public class ReportTemplateFileService {

  private final ReportTemplateConfig reportTemplateConfig;
  private final FileDownloader fileDownloader;
  private final DataExportConfig dataExportConfig;

  public ReportTemplateFileService(ReportTemplateConfig reportTemplateConfig,
                                   DataExportConfig dataExportConfig,
                                   FileDownloader fileDownloader) {
    this.reportTemplateConfig = reportTemplateConfig;
    this.fileDownloader = fileDownloader;
    this.dataExportConfig = dataExportConfig;
  }

  /**
   * Download a template from object-store using a toa (temporary object access).
   * @param objectStoreToa
   */
  public void downloadTemplate(String objectStoreToa) throws IOException {
    String downloadUrl = StringUtils.appendIfMissing(dataExportConfig.getObjectStoreDownloadUrl(), "/")
      + objectStoreToa;

    // call download
    fileDownloader.downloadFile(downloadUrl,
      filename -> Path.of(reportTemplateConfig.getTemplateFolder()).resolve(filename));
  }
}

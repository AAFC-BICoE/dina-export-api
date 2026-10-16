package ca.gc.aafc.dina.export.api.file;

import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.client.file.FileDownloader;
import ca.gc.aafc.dina.export.api.config.HttpClientConfig;

/**
 * Allows to download a file using token-based authentication.
 */
@Service
public class DinaFileDownloader extends FileDownloader {

  public DinaFileDownloader(HttpClientConfig openIdConnectConfig) {
    super(openIdConnectConfig, openIdConnectConfig.getAllowedHosts());
  }
}

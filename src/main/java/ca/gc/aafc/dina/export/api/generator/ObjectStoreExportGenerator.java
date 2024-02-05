package ca.gc.aafc.dina.export.api.generator;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.log4j.Log4j2;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.client.AccessTokenAuthenticator;
import ca.gc.aafc.dina.client.TokenBasedRequestBuilder;
import ca.gc.aafc.dina.client.token.AccessTokenManager;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.HttpClientConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;

@Service
@Log4j2
public class ObjectStoreExportGenerator extends DataExportGenerator {

  private final Path workingFolder;

  private final OkHttpClient httpClient;

  private final DataExportConfig dataExportConfig;
  private final TokenBasedRequestBuilder tokenBasedRequestBuilder;

  public ObjectStoreExportGenerator(HttpClientConfig openIdConnectConfig, DataExportConfig dataExportConfig,
                                    DataExportStatusService dataExportStatusService) {

    super(dataExportStatusService);

    this.dataExportConfig = dataExportConfig;
    AccessTokenManager accessTokenManager = new AccessTokenManager(openIdConnectConfig);
    httpClient = new OkHttpClient.Builder()
      .authenticator(new AccessTokenAuthenticator(accessTokenManager))
      .build();
    tokenBasedRequestBuilder = new TokenBasedRequestBuilder(accessTokenManager);
    workingFolder = dataExportConfig.getGeneratedDataExportsPath();
  }

  @Async(DataExportConfig.DINA_THREAD_POOL_BEAN_NAME)
  @Override
  public CompletableFuture<UUID> export(DataExport dinaExport) throws IOException {

    // Prepare url
    HttpUrl baseUrl = HttpUrl.parse(dataExportConfig.getObjectStoreDownloadUrl());
    if (baseUrl == null) {
      throw new IllegalStateException("Can't parse ObjectStoreDownloadUrl");
    }
    HttpUrl toaUrl = baseUrl.newBuilder()
      .addPathSegment(dinaExport.getTransitiveData()
        .get(DataExportConfig.OBJECT_STORE_TOA))
      .build();

    // make sure the destination folder exists
    if(!workingFolder.toFile().exists()) {
      Files.createDirectories(workingFolder);
    }

    DataExport.ExportStatus currStatus = waitForRecord(dinaExport.getUuid());

    if(currStatus == DataExport.ExportStatus.NEW) {

      updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.RUNNING);

      Call downloadCall =
        httpClient.newCall(tokenBasedRequestBuilder.newBuilder().url(toaUrl).build());
      try (Response response = downloadCall.execute()) {

        Path destinationFile = workingFolder.resolve(dinaExport.getUuid().toString() +
          extractFileExtensionFromResponse(response));

        ResponseBody body = response.body();
        if (!response.isSuccessful() || body == null) {
          throw new IllegalStateException("Can't read response body from object-store");
        }
        try (OutputStream outputStream = new FileOutputStream(destinationFile.toFile());
             InputStream inputStream = body.byteStream()) {
          IOUtils.copy(inputStream, outputStream);
          updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.COMPLETED);
        }
      } catch (IOException | IllegalStateException ex) {
        updateStatus(dinaExport.getUuid(), DataExport.ExportStatus.ERROR);
        throw ex;
      }
    } else {
      log.error("Unexpected DataExport status: {}", currStatus);
    }

    return CompletableFuture.completedFuture(dinaExport.getUuid());
  }

  private static String extractFileExtensionFromResponse(Response response) {
    String ext = "";
    String contentDisposition = response.header(HttpHeaders.CONTENT_DISPOSITION);
    if (contentDisposition != null) {
      ContentDisposition cd = ContentDisposition.parse(contentDisposition);
      if (StringUtils.isNotBlank(cd.getFilename())) {
        ext = FilenameUtils.getExtension(cd.getFilename());
      }
    }
    return "." + StringUtils.defaultIfBlank(ext, "tmp");
  }

}

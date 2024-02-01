package ca.gc.aafc.dina.export.api.generator;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Response;

import org.apache.commons.io.IOUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.client.AccessTokenAuthenticator;
import ca.gc.aafc.dina.client.TokenBasedRequestBuilder;
import ca.gc.aafc.dina.client.token.AccessTokenManager;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.HttpClientConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;

@Service
@Log4j2
public class ObjectStoreExportGenerator implements DataExportGenerator {

  private final Path workingFolder;

  private final OkHttpClient httpClient;

  private final DataExportConfig dataExportConfig;
  private final TokenBasedRequestBuilder tokenBasedRequestBuilder;

  public ObjectStoreExportGenerator(HttpClientConfig openIdConnectConfig, DataExportConfig dataExportConfig) {
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
    HttpUrl toaUrl = baseUrl.newBuilder().addPathSegment(dinaExport.getTransitiveData().get(DataExportConfig.OBJECT_STORE_TOA)).build();

    Path destinationFile = workingFolder.resolve(dinaExport.getUuid().toString());

    try (Response response = httpClient.newCall(tokenBasedRequestBuilder.newBuilder().url(toaUrl).build()).execute();
         OutputStream outputStream = new FileOutputStream(destinationFile.toFile());
         InputStream inputStream = response.body().byteStream()) {

      IOUtils.copy(inputStream, outputStream);
    }

    return CompletableFuture.completedFuture(dinaExport.getUuid());
  }
}

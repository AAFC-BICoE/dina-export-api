package ca.gc.aafc.dina.export.api.file;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.client.AccessTokenAuthenticator;
import ca.gc.aafc.dina.client.TokenBasedRequestBuilder;
import ca.gc.aafc.dina.client.token.AccessTokenManager;
import ca.gc.aafc.dina.export.api.config.HttpClientConfig;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Allows to download a file using token-based authentication.
 */
@Service
@Log4j2
public class FileDownloader {

  private final OkHttpClient httpClient;
  private final TokenBasedRequestBuilder tokenBasedRequestBuilder;

  public FileDownloader(HttpClientConfig openIdConnectConfig) {
    AccessTokenManager accessTokenManager = new AccessTokenManager(openIdConnectConfig);
    httpClient = new OkHttpClient.Builder()
      .authenticator(new AccessTokenAuthenticator(accessTokenManager))
      .build();
    tokenBasedRequestBuilder = new TokenBasedRequestBuilder(accessTokenManager);
  }

  /**
   * Download function.
   * @param downloadUrl url where to download the file from
   * @param destinationFileFunction function to return the Path of the destination file when given the filename received from the download.
   */
  public void downloadFile(String downloadUrl, Function<String, Path> destinationFileFunction) throws IOException {
    // Prepare url
    HttpUrl parsedDownloadUrl = HttpUrl.parse(downloadUrl);
    if (parsedDownloadUrl == null) {
      throw new IllegalStateException("Can't parse downloadUrl");
    }

    Call downloadCall =
      httpClient.newCall(tokenBasedRequestBuilder.newBuilder().url(parsedDownloadUrl).build());

    try (Response response = downloadCall.execute()) {

      Path destinationFile = destinationFileFunction.apply(extractFilenameFromResponse(response));

      ResponseBody body = response.body();
      if (!response.isSuccessful() || body == null) {
        throw new IllegalStateException("Can't read response body from downloadUrl. Returned code: " + response.code());
      }
      try (OutputStream outputStream = new FileOutputStream(destinationFile.toFile());
           InputStream inputStream = body.byteStream()) {
        IOUtils.copy(inputStream, outputStream);
      }
    }
  }

  private static String extractFilenameFromResponse(Response response) {
    String contentDisposition = response.header(HttpHeaders.CONTENT_DISPOSITION);
    if (contentDisposition != null) {
      ContentDisposition cd = ContentDisposition.parse(contentDisposition);
      if (StringUtils.isNotBlank(cd.getFilename())) {
        return cd.getFilename();
      }
    }
    return null;
  }
}

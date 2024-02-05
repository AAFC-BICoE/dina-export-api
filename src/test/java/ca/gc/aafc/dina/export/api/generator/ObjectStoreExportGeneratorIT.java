package ca.gc.aafc.dina.export.api.generator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterBody;
import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.core.JsonProcessingException;

import ca.gc.aafc.dina.client.token.AccessToken;
import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.async.AsyncConsumer;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.testsupport.TestResourceHelper;

@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = {8080, 8081})
public class ObjectStoreExportGeneratorIT extends BaseIntegrationTest {

  private static final String TEST_TOA = "sfsHG5eFW";

  private final ClientAndServer mockServer;

  public ObjectStoreExportGeneratorIT(ClientAndServer client) {
    this.mockServer = client;
  }

  @Inject
  private DataExportServiceTransactionWrapper dataExportServiceTransactionWrapper;

  @Inject
  private AsyncConsumer<Future<UUID>> asyncConsumer;

  @Test
  public void test() throws IOException {

    mockKeycloak(mockServer);
    mockObjectStoreDownloadResponse(mockServer, TEST_TOA, "/barcodes/06-01001016875.png");

    UUID uuid = UUID.randomUUID();
    DataExport dataExport = new DataExport();
    dataExport.setUuid(uuid);
    dataExport.setExportType(DataExport.ExportType.OBJECT_ARCHIVE);
    dataExport.setCreatedBy("test-user");
    dataExport.setSource(DataExportConfig.OBJECT_STORE_SOURCE);
    dataExport.setTransitiveData(Map.of(DataExportConfig.OBJECT_STORE_TOA, TEST_TOA));

    dataExportServiceTransactionWrapper.createEntity(dataExport);

    try {
      asyncConsumer.getAccepted().get(0).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private static void mockObjectStoreDownloadResponse(ClientAndServer mockServer, String toa, String resource)
    throws IOException {

    HttpHeaders respHeaders = new HttpHeaders();
    respHeaders.setContentDispositionFormData("attachment", FilenameUtils.getName(resource));

    var mockResponse = HttpResponse.response()
      .withHeader(HttpHeaders.CONTENT_DISPOSITION, respHeaders.getFirst(HttpHeaders.CONTENT_DISPOSITION))
      .withStatusCode(200);
    try (InputStream is = ObjectStoreExportGeneratorIT.class.getResourceAsStream(resource)) {
      if (is != null) {
        mockResponse.withBody(is.readAllBytes());
      }
    }
    mockResponse.withDelay(TimeUnit.SECONDS, 1);

    mockServer.when(setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/toa/" + toa))
      .respond(mockResponse);
  }

  public static void mockKeycloak(ClientAndServer mockServer) throws JsonProcessingException {

    AccessToken mockAccessToken = new AccessToken();
    mockAccessToken.setAccessToken("abc");

    ParameterBody params = new ParameterBody();
    Parameter clientId = new Parameter("client_id", "objectstore");
    Parameter username = new Parameter("username", "cnc-cm");
    Parameter password = new Parameter("password", "cnc-cm");
    Parameter grantType = new Parameter("grant_type", "password");
    ParameterBody.params(clientId, username, password, grantType);


    // Expectation for Authentication Token
    mockServer
      .when(
        HttpRequest.request()
          .withMethod("POST")
          .withPath("/auth/realms/dina/protocol/openid-connect/token")
          .withHeader("Content-type", "application/x-www-form-urlencoded")
          .withHeader("Connection", "Keep-Alive")
          .withBody(params))
      .respond(HttpResponse.response().withStatusCode(200)
        .withHeaders(
          new Header("Content-Type", "application/json; charset=utf-8"),
          new Header("Cache-Control", "public, max-age=86400"))
        .withBody(TestResourceHelper.OBJECT_MAPPER.writeValueAsString(mockAccessToken))
        .withDelay(TimeUnit.SECONDS, 1));
  }

  /**
   * Helper method that generates a mock request with the following headers:
   *    Authorization: Bearer with the fake keycloak access token.
   *    crnk-compact: true
   *    Connection: Keep-Alive
   *    Accept-Encoding: application/json
   * @return
   */
  public static HttpRequest setupMockRequest() {
    return HttpRequest.request()
      .withHeader("Authorization", "Bearer " + "abc")
      .withHeader("Connection", "Keep-Alive");
  }
}

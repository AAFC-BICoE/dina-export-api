package ca.gc.aafc.dina.export.api.generator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

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
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.client.token.AccessToken;
import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.async.AsyncConsumer;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;

@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = {8080, 8081})
public class ObjectStoreExportGeneratorIT extends BaseIntegrationTest {

  private final ClientAndServer mockServer;

  public ObjectStoreExportGeneratorIT(ClientAndServer client) {
    this.mockServer = client;
  }

  @Inject
  private DataExportServiceTransactionWrapper dataExportServiceTransactionWrapper;

  @Inject
  private AsyncConsumer<Future<UUID>> asyncConsumer;

  @Test
  public void test() throws JsonProcessingException {

    mockKeycloak(mockServer);
    mockObjectStoreResponse(mockServer);

    UUID uuid = UUID.randomUUID();
    DataExport dataExport = new DataExport();
    dataExport.setUuid(uuid);
    dataExport.setExportType(DataExport.ExportType.OBJECT_ARCHIVE);
    dataExport.setCreatedBy("test-user");
    dataExport.setSource(DataExportConfig.OBJECT_STORE_SOURCE);
    dataExport.setTransitiveData(Map.of(DataExportConfig.OBJECT_STORE_TOA, "abc"));

    dataExportServiceTransactionWrapper.createEntity(dataExport);

    try {
      asyncConsumer.getAccepted().get(0).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private static void mockObjectStoreResponse(ClientAndServer mockServer) {
    mockServer.when(setupMockRequest()
        .withMethod("GET")
        .withPath("/api/v1/toa/"))
      .respond(HttpResponse.response().withStatusCode(200)
        .withBody("")
        .withDelay(TimeUnit.SECONDS, 1));
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

    ObjectMapper OM = new ObjectMapper();

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
        .withBody(OM.writeValueAsString(mockAccessToken))
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
      .withHeader("crnk-compact", "true")
      .withHeader("Connection", "Keep-Alive")
      .withHeader("Accept-Encoding", "application/json");
  }
}

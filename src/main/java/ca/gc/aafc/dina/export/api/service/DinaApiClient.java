package ca.gc.aafc.dina.export.api.service;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.client.AccessTokenAuthenticator;
import ca.gc.aafc.dina.client.TokenBasedRequestBuilder;
import ca.gc.aafc.dina.client.token.AccessTokenManager;
import ca.gc.aafc.dina.export.api.config.HttpClientConfig;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Generic token-authenticated client for DINA JSON:API GET requests.
 *
 * Fetches a JSON:API document from any DINA API endpoint and parses it into a
 * {@link JsonApiDocument}. Results are cached by URL since reference data rarely changes.
 */
@Service
@Log4j2
public class DinaApiClient {

  private final OkHttpClient httpClient;
  private final TokenBasedRequestBuilder tokenBasedRequestBuilder;
  private final ObjectMapper objectMapper;

  // Cache resolved documents by URL. computeIfAbsent does not cache null results,
  // so failed lookups are retried on subsequent calls.
  private final Map<String, JsonApiDocument> documentCache = new ConcurrentHashMap<>();

  public DinaApiClient(HttpClientConfig httpClientConfig, ObjectMapper objectMapper) {
    AccessTokenManager accessTokenManager = new AccessTokenManager(httpClientConfig);
    this.httpClient = new OkHttpClient.Builder()
      .authenticator(new AccessTokenAuthenticator(accessTokenManager))
      .build();
    this.tokenBasedRequestBuilder = new TokenBasedRequestBuilder(accessTokenManager);
    this.objectMapper = objectMapper;
  }

  /**
   * Performs a token-authenticated GET on the given URL and parses the response into a
   * {@link JsonApiDocument}.
   *
   * <p>If the response {@code data} is a collection (e.g. a filter query), the first element
   * is used. Returns null if the request fails, the body is empty, or no data is present.</p>
   *
   * @param url the target URL
   * @return the parsed document, or null if not found or on error
   */
  public JsonApiDocument fetchDocument(HttpUrl url) {
    if (url == null) {
      return null;
    }
    return documentCache.computeIfAbsent(url.toString(), this::doFetchDocument);
  }

  private JsonApiDocument doFetchDocument(String url) {
    HttpUrl parsedUrl = HttpUrl.parse(url);
    log.debug("Fetching JSON:API document: {}", parsedUrl);
    try (Response response = httpClient.newCall(tokenBasedRequestBuilder.newBuilder().url(parsedUrl).build()).execute()) {
      if (!response.isSuccessful()) {
        log.warn("Failed to fetch {}: status {}", parsedUrl, response.code());
        return null;
      }

      ResponseBody body = response.body();
      if (body == null) {
        log.warn("No response body from {}", parsedUrl);
        return null;
      }

      JsonNode root = objectMapper.readTree(body.string());
      JsonNode dataNode = root.path("data");
      if (dataNode.isArray()) {
        dataNode = dataNode.isEmpty() ? null : dataNode.get(0);
      }
      if (dataNode == null || dataNode.isMissingNode() || dataNode.isNull()) {
        log.warn("No data in response from {}", parsedUrl);
        return null;
      }

      JsonApiDocument.ResourceObject resourceObject =
        objectMapper.convertValue(dataNode, JsonApiDocument.ResourceObject.class);
      log.debug("Resolved document from {}: type={}, id={}", parsedUrl,
        resourceObject.getType(), resourceObject.getId());
      return JsonApiDocument.builder().data(resourceObject).build();
    } catch (IOException ioEx) {
      log.warn("Error fetching document from {}", parsedUrl, ioEx);
      return null;
    }
  }
}

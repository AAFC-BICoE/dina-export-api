package ca.gc.aafc.dina.export.api.service;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.client.AccessTokenAuthenticator;
import ca.gc.aafc.dina.client.TokenBasedRequestBuilder;
import ca.gc.aafc.dina.client.token.AccessTokenManager;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.config.HttpClientConfig;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Client to fetch reference data (controlled-vocabulary-item) from the object store API.
 *
 * Managed attribute definitions (e.g. uriTemplate) are no longer embedded in the metadata
 * documents; they are served by the object store API as controlled-vocabulary-item resources.
 * This client fetches them on demand and caches the result since they rarely change.
 */
@Service
@Log4j2
public class ObjectStoreApiClient {

  private static final String CONTROLLED_VOCABULARY_ITEM_ENDPOINT = "controlled-vocabulary-item";
  private static final String METADATA_COMPONENT = "METADATA";

  private final OkHttpClient httpClient;
  private final TokenBasedRequestBuilder tokenBasedRequestBuilder;
  private final DataExportConfig dataExportConfig;
  private final ObjectMapper objectMapper;

  // Cache resolved items by key. computeIfAbsent does not cache null results,
  // so failed lookups are retried on subsequent calls.
  private final Map<String, JsonNode> itemCache = new ConcurrentHashMap<>();

  public ObjectStoreApiClient(HttpClientConfig httpClientConfig, DataExportConfig dataExportConfig,
                              ObjectMapper objectMapper) {
    AccessTokenManager accessTokenManager = new AccessTokenManager(httpClientConfig);
    this.httpClient = new OkHttpClient.Builder()
      .authenticator(new AccessTokenAuthenticator(accessTokenManager))
      .build();
    this.tokenBasedRequestBuilder = new TokenBasedRequestBuilder(accessTokenManager);
    this.dataExportConfig = dataExportConfig;
    this.objectMapper = objectMapper;
  }

  /**
   * Returns the controlled-vocabulary-item data node matching the given managed attribute key
   * and the METADATA component.
   *
   * @param key managed attribute key (e.g. "ena_run_accession")
   * @return the item's data node or null if not found or on error
   */
  public JsonNode getControlledVocabularyItem(String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    return itemCache.computeIfAbsent(key, this::fetchControlledVocabularyItem);
  }

  private JsonNode fetchControlledVocabularyItem(String key) {
    HttpUrl url = buildControlledVocabularyItemUrl(key);
    if (url == null) {
      return null;
    }

    log.debug("Fetching controlled-vocabulary-item for key '{}': {}", key, url);
    try (Response response = httpClient.newCall(tokenBasedRequestBuilder.newBuilder().url(url).build()).execute()) {
      if (!response.isSuccessful()) {
        log.warn("Failed to fetch {} for key {}: status {}", CONTROLLED_VOCABULARY_ITEM_ENDPOINT, key, response.code());
        return null;
      }

      ResponseBody body = response.body();
      if (body == null) {
        log.warn("No response body for controlled-vocabulary-item key '{}' ({})", key, url);
        return null;
      }

      JsonNode root = objectMapper.readTree(body.string());
      JsonNode data = root.at("/data/0");
      if (data.isMissingNode()) {
        log.warn("No matching controlled-vocabulary-item for key '{}' ({})", key, url);
        return null;
      }
      log.debug("Resolved controlled-vocabulary-item for key '{}': id={}", key, data.path("id").asText());
      return data;
    } catch (IOException ioEx) {
      log.warn("Error fetching {} for key {}", CONTROLLED_VOCABULARY_ITEM_ENDPOINT, key, ioEx);
      return null;
    }
  }

  private HttpUrl buildControlledVocabularyItemUrl(String key) {
    String baseUrl = dataExportConfig.getObjectStoreApiUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      log.warn("objectStoreApiUrl is not configured, unable to resolve managed attribute key {}", key);
      return null;
    }

    HttpUrl url = HttpUrl.parse(baseUrl);
    if (url == null) {
      log.warn("Invalid objectStoreApiUrl: {}", baseUrl);
      return null;
    }

    return url.newBuilder()
      .addPathSegment(CONTROLLED_VOCABULARY_ITEM_ENDPOINT)
      .addQueryParameter("filter[key]", key)
      .addQueryParameter("filter[dinaComponent]", METADATA_COMPONENT)
      .build();
  }
}

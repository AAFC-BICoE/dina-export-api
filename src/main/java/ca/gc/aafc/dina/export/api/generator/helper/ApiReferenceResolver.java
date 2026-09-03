package ca.gc.aafc.dina.export.api.generator.helper;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ca.gc.aafc.dina.export.api.config.ApiReference;
import ca.gc.aafc.dina.export.api.service.DinaApiClient;
import ca.gc.aafc.dina.json.JsonHelper;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;

/**
 * Resolves values that are backed by an external DINA API reference (e.g. a
 * controlled-vocabulary-item).
 *
 * <p>Each element of a to-many context (e.g. an attachment array) holds a map of managed
 * attribute keys to values. For each key, the matching document is fetched from the external
 * API (and cached) and the configured {@code vocabularyValue} field (e.g. uriTemplate) is
 * extracted. The {@code valuePlaceholder} in that field is replaced with the actual managed
 * attribute value. Resolved values are returned as a list; combining them (e.g. joining with
 * a separator) is left to the caller.
 *
 * <p>Kept as its own component so it can be reused by other exporters that resolve values
 * from external DINA APIs, without being tied to DarwinCore-specific mapping logic.
 */
@Component
@Log4j2
public class ApiReferenceResolver {

  private static final String FILTER_KEY_PARAM = "filter[key]";
  private static final String DINA_COMPONENT_PARAM = "dinaComponent";

  private final DinaApiClient dinaApiClient;

  public ApiReferenceResolver(DinaApiClient dinaApiClient) {
    this.dinaApiClient = dinaApiClient;
  }

  /**
   * Resolves the values referenced by an external DINA API.
   *
   * Each element of the (to-many) {@code contextNode} is expected to hold, at the given
   * {@code sourcePath}, an object mapping attribute keys to values. For each key (optionally
   * restricted to {@code apiReference.getVocabularyKey()}) the referenced document is fetched
   * from the external API and the configured {@code vocabularyValue} field is extracted. The
   * {@code valuePlaceholder} in that field is replaced with the actual attribute value.
   *
   * <p>The resolved values are returned as a list in source order. How they are combined
   * (e.g. joined with a separator) is left to the caller.
   *
   * @param contextNode the to-many context node (array)
   * @param apiReference config describing how to resolve each value through the external API
   * @param sourcePath JSONPath (relative to each array element) to the object holding the key-value pairs
   * @return the resolved values in source order, or an empty list if none could be resolved
   */
  public List<String> resolveApiReferencedValues(JsonNode contextNode, ApiReference apiReference,
      String sourcePath) {
    if (apiReference == null) {
      log.warn("No apiReference configured");
      return List.of();
    }

    if (!contextNode.isArray()) {
      log.warn("API referenced value resolution requires an array context, but {} is not an array",
        contextNode.getNodeType());
      return List.of();
    }

    List<String> resolvedValues = new ArrayList<>();
    for (JsonNode element : contextNode) {
      JsonNode attributeValues = JsonHelper.findOneInJsonNode(element, "$." + sourcePath);
      if (attributeValues == null || !attributeValues.isObject()) {
        continue;
      }

      attributeValues.properties().forEach(entry -> {
        if (apiReference.getVocabularyKey() != null && !apiReference.getVocabularyKey().equals(entry.getKey())) {
          return;
        }

        if (entry.getValue() == null || entry.getValue().isNull()) {
          return;
        }

        HttpUrl url = buildReferencedValueUrl(apiReference, entry.getKey());
        if (url == null) {
          return;
        }
        JsonApiDocument doc = dinaApiClient.fetchDocument(url);
        if (doc == null || doc.getAttributes() == null) {
          log.warn("No referenced document found for key '{}'", entry.getKey());
          return;
        }

        Object referencedValue = doc.getAttributes().get(apiReference.getVocabularyValue());
        if (referencedValue == null) {
          log.warn("Referenced document for key '{}' has no '{}' attribute",
            entry.getKey(), apiReference.getVocabularyValue());
          return;
        }

        resolvedValues.add(String.valueOf(referencedValue)
          .replace(apiReference.getValuePlaceholder(), entry.getValue().asText()));
      });
    }
    return resolvedValues;
  }

  /**
   * Builds the URL to fetch a referenced document.
   *
   * The base URL comes from the apiReference's {@code vocabularyUrl}, e.g.
   * "${dina.export.objectStoreApiUrl}/controlled-vocabulary-item". The {@code filter[key]} query
   * parameter is added with the managed attribute key, and the optional {@code dinaComponent}
   * filter value is added when configured.
   *
   * @param apiReference the api reference config that defines the referenced document URL
   * @param key the value to filter on, typically a managed attribute key
   * @return the URL, or null if no URL is configured or it is invalid
   */
  private HttpUrl buildReferencedValueUrl(ApiReference apiReference, String key) {
    String url = apiReference.getVocabularyUrl();
    if (url == null || url.isBlank()) {
      log.warn("No vocabularyUrl configured for column resolving key {}", key);
      return null;
    }

    HttpUrl parsedUrl = HttpUrl.parse(url);
    if (parsedUrl == null) {
      log.warn("Invalid vocabularyUrl: {}", url);
      return null;
    }

    HttpUrl.Builder urlBuilder = parsedUrl.newBuilder()
      .addQueryParameter(FILTER_KEY_PARAM, key);
    if (apiReference.getDinaComponent() != null && !apiReference.getDinaComponent().isBlank()) {
      urlBuilder.addQueryParameter(DINA_COMPONENT_PARAM, apiReference.getDinaComponent());
    }
    return urlBuilder.build();
  }
}

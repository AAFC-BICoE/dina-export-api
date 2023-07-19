package ca.gc.aafc.dina.export.api.source;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.ClosePointInTimeRequest;
import co.elastic.clients.elasticsearch.core.ClosePointInTimeResponse;
import co.elastic.clients.elasticsearch.core.OpenPointInTimeResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/**
 * ElasticSearch-backed source of data.
 * Data is returned as {@link JsonNode} since the export is agnostic of the type of data.
 */
@Component
public class ElasticSearchDataSource {

  public static final int ES_PAGE_SIZE = 5;
  private static final Time KEEP_ALIVE = new Time.Builder().time("60s").build();

  private final ElasticsearchClient client;

  public ElasticSearchDataSource(ElasticsearchClient client) {
    this.client = client;
  }

  public SearchResponse<JsonNode> search(String indexName, String query) throws IOException {
    Reader strReader = new StringReader(query);
    SearchRequest sr = SearchRequest.of(b -> b
      .withJson(strReader).index(indexName));

    return client.search(sr, JsonNode.class);
  }

  /**
   * Search with ElasticSearch Point-in-time to go through multiple pages.
   * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/7.17/paginate-search-results.html#search-after">https://www.elastic.co/guide/en/elasticsearch/reference/7.17/paginate-search-results.html#search-after</a>
   *
   * For the next pages, {@link #searchAfter(String, String, List)} should be used.
   *
   * @param indexName
   * @param query
   * @return
   */
  public SearchResponse<JsonNode> searchWithPIT(String indexName, String query) throws IOException {

    // create the PIT
    OpenPointInTimeResponse opitResponse =
      client.openPointInTime(b -> b.index(indexName).keepAlive(KEEP_ALIVE));

    Reader strReader = new StringReader(query);
    SearchRequest sr = SearchRequest.of(b -> b
      .withJson(strReader)
      .size(ES_PAGE_SIZE)
      .pit(pit -> pit.id(opitResponse.id()).keepAlive(KEEP_ALIVE)));
    return client.search(sr, JsonNode.class);
  }

  /**
   * Search after (next page) with ElasticSearch Point-in-time.
   * @param query should match what was provided to the initial {@link #searchWithPIT(String, String)} call
   * @param pitId returned from the initial {@link #searchWithPIT(String, String)} call
   * @param sortFieldValues returned from the initial {@link #searchWithPIT(String, String)} call
   * @return
   */
  public SearchResponse<JsonNode> searchAfter(String query, String pitId, List<FieldValue> sortFieldValues) throws IOException {
    Reader strReader = new StringReader(query);
    SearchRequest sr = SearchRequest.of(b -> b
      .withJson(strReader)
      .size(ES_PAGE_SIZE)
      .searchAfter(sortFieldValues)
      .pit(pit -> pit.keepAlive(KEEP_ALIVE).id(pitId)));
    return client.search(sr, JsonNode.class);
  }

  /**
   * Close a previously opened PIT.
   * @param pitId
   * @return
   */
  public boolean closePointInTime(String pitId) throws IOException {
    ClosePointInTimeRequest request = ClosePointInTimeRequest.of(b -> b
      .id(pitId));
    ClosePointInTimeResponse csr = client.closePointInTime(request);
    return csr.succeeded();
  }

}

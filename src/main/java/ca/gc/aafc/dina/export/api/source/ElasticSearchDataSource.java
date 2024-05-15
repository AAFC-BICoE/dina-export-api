package ca.gc.aafc.dina.export.api.source;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
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

  public static final int ES_DEFAULT_PAGE_SIZE = 10;
  private static final Time KEEP_ALIVE = new Time.Builder().time("60s").build();
  private static final SortOptions DEFAULT_SORT =
    new SortOptions.Builder().field(fs -> fs.field("_id").order(SortOrder.Asc)).build();

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

    SearchRequest sr = buildSearchRequestWithPIT(opitResponse.id(), query, false);

    //We need a sort so if the query doesn't include one, use the default one
    if(CollectionUtils.isEmpty(sr.sort())) {
      sr = buildSearchRequestWithPIT(opitResponse.id(), query, true);
    }

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

    SearchRequest sr = buildSearchRequestWithPIT(pitId, query, false, sortFieldValues);
    //We need a sort so if the query doesn't include one, use the default one
    if(CollectionUtils.isEmpty(sr.sort())) {
      sr = buildSearchRequestWithPIT(pitId, query, true, sortFieldValues);
    }
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

  private static SearchRequest buildSearchRequestWithPIT(String pitId, String query, boolean setDefaultSort) {
    return buildSearchRequestWithPIT(pitId, query, setDefaultSort, null);
  }

  private static SearchRequest buildSearchRequestWithPIT(String pitId, String query, boolean setDefaultSort, List<FieldValue> searchAfter) {
    Reader strReader = new StringReader(query);
    SearchRequest.Builder builder = new SearchRequest.Builder();
    builder.withJson(strReader)
      .size(ES_DEFAULT_PAGE_SIZE)
      .pit(pit -> pit.id(pitId).keepAlive(KEEP_ALIVE));

    if(CollectionUtils.isNotEmpty(searchAfter)) {
      builder.searchAfter(searchAfter);
    }

    if(setDefaultSort) {
      builder.sort(DEFAULT_SORT);
    }

    return SearchRequest.of(b -> builder);
  }

}

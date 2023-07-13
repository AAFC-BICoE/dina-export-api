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

@Component
public class ElasticSearchDataSource {

  public static final int ES_PAGE_SIZE = 5;
  private static final Time KEEP_ALIVE = new Time.Builder().time("60s").build();

  private final ElasticsearchClient client;

  public ElasticSearchDataSource(ElasticsearchClient client){
    this.client = client;
  }

  public SearchResponse<JsonNode> search(String indexName, String query) throws IOException {
    Reader strReader = new StringReader(query);
    SearchRequest sr = SearchRequest.of(b -> b
      .withJson(strReader).index(indexName));

    return client.search(sr, JsonNode.class);
  }

  /**
   * Search with ElasticSearch Point-in-time
   * @param indexName
   * @param query
   * @return
   * @throws IOException
   */
  public SearchResponse<JsonNode> searchWithPIT(String indexName, String query) throws IOException {

    OpenPointInTimeResponse opitResponse =
      client.openPointInTime(b -> b.index(indexName).keepAlive(KEEP_ALIVE));

    Reader strReader = new StringReader(query);
    SearchRequest sr = SearchRequest.of(b -> b
      .withJson(strReader)
      .size(ES_PAGE_SIZE)
      .pit(pit -> pit.id(opitResponse.id()).keepAlive(KEEP_ALIVE)));
    return client.search(sr, JsonNode.class);
  }

  public SearchResponse<JsonNode> searchAfter(String indexName, String query, String pitId, List<FieldValue> sortFieldValues) throws IOException {
    Reader strReader = new StringReader(query);
    SearchRequest sr = SearchRequest.of(b -> b
      .withJson(strReader)
      .index(indexName)
      .size(ES_PAGE_SIZE)
      .searchAfter(sortFieldValues)
      .pit(pit -> pit.keepAlive(KEEP_ALIVE).id(pitId)));
    return client.search(sr, JsonNode.class);
  }

  public boolean closePointInTime(String pitId) throws IOException {
    ClosePointInTimeRequest request = ClosePointInTimeRequest.of(b -> b
      .id(pitId));
    ClosePointInTimeResponse csr = client.closePointInTime(request);
    return csr.succeeded();
  }

//  public SearchResponse<JsonNode> searchWithScroll(String indexName, String query) throws IOException {
//    Reader strReader = new StringReader(query);
//    SearchRequest sr = SearchRequest.of(b -> b
//      .withJson(strReader)
//      .index(indexName)
//      .size(ES_SCROLL_SIZE)
//      .scroll(new Time.Builder().time("60s").build()));
//    return client.search(sr, JsonNode.class);
//  }

//  public ScrollResponse<JsonNode> scroll(String scrollId) throws IOException {
//    ScrollRequest sr = ScrollRequest.of( b -> b
//      .scrollId(scrollId)
//      .scroll(new Time.Builder().time("60s").build())
//    );
//    return client.scroll(sr, JsonNode.class);
//  }

//  public boolean clearScroll(String scrollId) throws IOException {
//    ClearScrollRequest request = ClearScrollRequest.of( b -> b
//      .scrollId(scrollId));
//    ClearScrollResponse csr = client.clearScroll(request);
//    return csr.succeeded();
//  }


}

package ca.gc.aafc.dina.export.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import okhttp3.HttpUrl;

public class ExternalRelationshipResolverTest {

  private static final String AGENT_API_URL = "http://localhost:8082/api/v1";
  private static final String OBJECT_STORE_API_URL = "http://localhost:8081/api/v1";

  @Test
  public void resolve_onConfiguredTypes_fetchedFromTheirApi() {
    UUID person = UUID.randomUUID();
    UUID derivative = UUID.randomUUID();
    DinaApiClient client = mock(DinaApiClient.class);
    when(client.fetchDocument(HttpUrl.parse(AGENT_API_URL + "/person/" + person)))
      .thenReturn(document("person", person));
    when(client.fetchDocument(HttpUrl.parse(OBJECT_STORE_API_URL + "/derivative/" + derivative)))
      .thenReturn(document("derivative", derivative));

    ExternalRelationshipResolver resolver = newResolver(client);

    assertEquals(person, resolver.resolve("person", person).getId());
    assertEquals(derivative, resolver.resolve("derivative", derivative).getId());
  }

  @Test
  public void resolve_onTypeNotConfigured_notFetched() {
    DinaApiClient client = mock(DinaApiClient.class);

    assertNull(newResolver(client).resolve("expedition", UUID.randomUUID()));
    verify(client, never()).fetchDocument(any(HttpUrl.class));
  }

  @Test
  public void resolve_onInclude_includeInUrl() {
    UUID person = UUID.randomUUID();
    DinaApiClient client = mock(DinaApiClient.class);
    when(client.fetchDocument(HttpUrl.parse(AGENT_API_URL + "/person/" + person + "?include=organizations")))
      .thenReturn(document("person", person));

    assertEquals(person, newResolver(client).resolve("person", person, "organizations").getId());
  }

  private static ExternalRelationshipResolver newResolver(DinaApiClient client) {
    return new ExternalRelationshipResolver(client, AGENT_API_URL, "person,organization",
      OBJECT_STORE_API_URL, "metadata, derivative");
  }

  private static JsonApiDocument document(String type, UUID uuid) {
    return JsonApiDocument.builder()
      .data(JsonApiDocument.ResourceObject.builder()
        .id(uuid)
        .type(type)
        .attributes(Map.of("name", "test"))
        .build())
      .build();
  }
}

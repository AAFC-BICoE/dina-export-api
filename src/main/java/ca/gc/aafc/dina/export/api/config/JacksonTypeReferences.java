package ca.gc.aafc.dina.export.api.config;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Reusable Jackson {@link com.fasterxml.jackson.core.type.TypeReference}
 */
public final class JacksonTypeReferences {

  public static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPEREF =
    new TypeReference<>() {
    };

  public static final TypeReference<Map<String, Object>> MAP_TYPEREF =
    new TypeReference<>() {
    };

  private JacksonTypeReferences() {
    // utility class
  }
}

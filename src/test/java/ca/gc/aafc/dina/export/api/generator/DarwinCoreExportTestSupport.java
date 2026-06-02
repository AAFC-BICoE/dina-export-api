package ca.gc.aafc.dina.export.api.generator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import ca.gc.aafc.dina.jsonapi.JSONApiDocumentStructure;

final class DarwinCoreExportTestSupport {

  private DarwinCoreExportTestSupport() {
  }

  static void generateArchive(
      DarwinCoreExportGenerator generator,
      List<JsonNode> esSources,
      Path outputPath) throws IOException {

    generator.preRecordWrite(null, outputPath);
    try {
      for (JsonNode source : esSources) {
        JsonNode data = source.path(JSONApiDocumentStructure.DATA);
        String id = data.path(JSONApiDocumentStructure.ID).asText(null);
        generator.processEntity(data, id, source, null, null);
      }
      generator.postRecordWrite(null, outputPath);
    } catch (IOException e) {
      throw e;
    }
  }
}
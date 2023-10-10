package ca.gc.aafc.dina.export.api.generator;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.DinaExportModuleApiLauncher;
import ca.gc.aafc.dina.export.api.generator.FreemarkerReportGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@SpringBootTest(classes = {BaseIntegrationTest.TestConfig.class, DinaExportModuleApiLauncher.class })
public class FreemarkerReportGeneratorIT extends BaseIntegrationTest {

  @Inject
  private FreemarkerReportGenerator freemarkerReportGenerator;

  @Test
  public void testCsvGeneration() throws IOException {
    Writer writer = new StringWriter();
    freemarkerReportGenerator.generateReport("testCsv.flt",
            Map.of("headers", List.of("h1", "h2", "h3"), "data", List.of(List.of("1", "2", "3"),List.of("11", "22", "33"))),
            writer);
    assertEquals("h1,h2,h31,2,311,22,33", StringUtils.deleteWhitespace(writer.toString()));
  }

  @Test
  public void testJsonGeneration() throws IOException {
    Writer writer = new StringWriter();
    freemarkerReportGenerator.generateReport("testJson.flt",
      Map.of("data", List.of(
        MyObject.builder()
          .sampleName("ABC-1")
          .extractName("b8")
          .date("2003-04-02").build(),
        MyObject.builder()
          .sampleName("ABC-2")
          .extractName("b45")
          .date("2003-04-03").build()
        )),
      writer);
    assertEquals("{\"payload\":[{\"lineId\":1,\"generatedName\":\"ABC-1_b8_2003-04-02\"},{\"lineId\":2,\"generatedName\":\"ABC-2_b45_2003-04-03\"}]}",
      StringUtils.deleteWhitespace(writer.toString()));
  }

  @Setter
  @Getter
  @Builder
  public static class MyObject {
    private String sampleName;
    private String extractName;
    private String date;
  }

}

package ca.gc.aafc.dina.export.api.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.inject.Inject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.DinaExportModuleApiLauncher;
import ca.gc.aafc.dina.export.api.generator.OpenhtmltopdfGenerator;

@SpringBootTest(classes = {BaseIntegrationTest.TestConfig.class, DinaExportModuleApiLauncher.class })
public class OpenhtmltopdfGeneratorIT extends BaseIntegrationTest {

  @Inject
  private OpenhtmltopdfGenerator pdfGenerator;

  @Test
  public void testHtmlToPDF() throws IOException {
    File tempFile = File.createTempFile("OpenhtmltopdfGeneratorTest", ".pdf");
    try (FileOutputStream fos = new FileOutputStream(tempFile)){
      pdfGenerator.generatePDF("<html>hello</html>", "", fos);
    }
  }
}

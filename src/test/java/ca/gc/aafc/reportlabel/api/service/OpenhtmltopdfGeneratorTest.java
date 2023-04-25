package ca.gc.aafc.reportlabel.api.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.inject.Inject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.reportlabel.api.BaseIntegrationTest;
import ca.gc.aafc.reportlabel.api.ReportLabelModuleApiLauncher;

@SpringBootTest(classes = {BaseIntegrationTest.TestConfig.class, ReportLabelModuleApiLauncher.class })
public class OpenhtmltopdfGeneratorTest {

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

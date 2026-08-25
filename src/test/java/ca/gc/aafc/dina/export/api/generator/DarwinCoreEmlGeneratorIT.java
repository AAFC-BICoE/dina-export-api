package ca.gc.aafc.dina.export.api.generator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ca.aafc.eml.generated.eml.Eml;

public class DarwinCoreEmlGeneratorIT {

  @Test
  public void testEml() throws IOException {
    DarwinCoreEmlGenerator darwinCoreEmlGenerator = new DarwinCoreEmlGenerator();

    Eml eml = new Eml();
    eml.setPackageId("1234");

    Path tempFile = Files.createTempFile("eml", ".xml");
    try {
      darwinCoreEmlGenerator.generateEml(tempFile, eml);
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }
}

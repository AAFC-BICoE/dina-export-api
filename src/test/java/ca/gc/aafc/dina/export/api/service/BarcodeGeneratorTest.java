package ca.gc.aafc.dina.export.api.service;

import com.google.zxing.WriterException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class BarcodeGeneratorTest {

  @Test
  public void testDataMatrixGeneration() throws IOException, WriterException {

    BarcodeGenerator barcodeGenerator = new BarcodeGenerator();

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    barcodeGenerator.createCode("06-01001016875", output, new BarcodeGenerator.CodeGenerationOption(600, 600,
            null, null, null, "square",
            BarcodeGenerator.CodeFormat.DATA_MATRIX));
    output.flush();
    byte[] content = output.toByteArray();

    byte[] expectedContent = null;
    try (InputStream is = this.getClass().getResourceAsStream("/barcodes/06-01001016875.png")) {
      if (is != null) {
        expectedContent = is.readAllBytes();
      }
    }
    assertArrayEquals(expectedContent, content);
  }

}

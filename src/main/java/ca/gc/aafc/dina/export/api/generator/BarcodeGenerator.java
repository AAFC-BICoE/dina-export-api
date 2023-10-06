package ca.gc.aafc.dina.export.api.generator;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.datamatrix.encoder.SymbolShapeHint;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class BarcodeGenerator {

  public enum CodeFormat { QR_CODE, CODE_128, DATA_MATRIX }

  public static final String CODE_OUTPUT_FORMAT = "png";

  private static final QRCodeWriter QR_CODE_WRITER = new QRCodeWriter();
  private static final Code128Writer BARCODE_WRITER = new Code128Writer();
  private static final DataMatrixWriter DATA_MATRIX_WRITER = new DataMatrixWriter();

  public static CodeGenerationOption buildDefaultQrConfig() {
    return new BarcodeGenerator.CodeGenerationOption(600, 600,
      null, null, null, "square",
      BarcodeGenerator.CodeFormat.DATA_MATRIX);
  }

  /**
   *
   * @param content data to encode
   * @param outputStream stream to write the generated image of the code
   * @param codeOptions options of the code
   * @throws WriterException
   * @throws IOException
   */
  public void createCode(String content, OutputStream outputStream, CodeGenerationOption codeOptions) throws WriterException, IOException {

    //set specific parameters
    Map<EncodeHintType,Object> hints = new HashMap<>();
    if(codeOptions.margin() != null ) {
      hints.put(EncodeHintType.MARGIN, codeOptions.margin());
    }

    if(codeOptions.dataMatrixShape() != null) {
      hints.put(EncodeHintType.DATA_MATRIX_SHAPE, switch (codeOptions.dataMatrixShape()) {
        case "square" -> SymbolShapeHint.FORCE_SQUARE;
        case "rectangle" -> SymbolShapeHint.FORCE_RECTANGLE;
        default -> SymbolShapeHint.FORCE_NONE;
      });
    }

    if(codeOptions.qrcodeVersion() != null ) {
      hints.put(EncodeHintType.QR_VERSION, codeOptions.qrcodeVersion());
    }

    /**
     L = ~7% correction
     M = ~15% correction
     Q = ~25% correction
     H = ~30% correction
     **/
    if (codeOptions.correction() != null) {
      hints.put(EncodeHintType.ERROR_CORRECTION, switch (codeOptions.correction()) {
        case "L" -> ErrorCorrectionLevel.L;
        case "M" -> ErrorCorrectionLevel.M;
        case "Q" -> ErrorCorrectionLevel.Q;
        case "H" -> ErrorCorrectionLevel.H;
        default -> throw new IllegalArgumentException("Only values from ErrorCorrectionLevel are accepted");
      });
    }

    BitMatrix bitMatrix = switch(codeOptions.format()) {
      case QR_CODE -> QR_CODE_WRITER.encode(content, BarcodeFormat.QR_CODE, codeOptions.width(), codeOptions.height(), hints);
      case CODE_128 -> BARCODE_WRITER.encode(content, BarcodeFormat.CODE_128, codeOptions.width(), codeOptions.height(), hints);
      case DATA_MATRIX -> DATA_MATRIX_WRITER.encode(content, BarcodeFormat.DATA_MATRIX, codeOptions.width(), codeOptions.height(), hints);
    };

    MatrixToImageWriter.writeToStream(bitMatrix, CODE_OUTPUT_FORMAT, outputStream);
  }

  /**
   * Record used to provide configuration for code generation.
   * @param width
   * @param height
   * @param margin
   * @param qrcodeVersion
   * @param correction
   * @param dataMatrixShape
   * @param format
   */
  public record CodeGenerationOption(Integer width, Integer height,
                                    Integer margin, Integer qrcodeVersion, String correction, String dataMatrixShape,
                                    CodeFormat format) {
  }

}

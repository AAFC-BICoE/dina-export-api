package ca.gc.aafc.reportlabel.api.service;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.stereotype.Service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

@Service
public class OpenhtmltopdfGenerator implements PDFGenerator {

  public static BaseRendererBuilder.PageSizeUnits pageUnit = BaseRendererBuilder.PageSizeUnits.MM;
  public float pageWidth = 210;
  public float pageHeight = 297;

  @Override
  public void generatePDF(String html, String baseDocumentUri, OutputStream os) throws IOException {

    PdfRendererBuilder builder = new PdfRendererBuilder();
    builder.withHtmlContent(html, baseDocumentUri);

    builder.useDefaultPageSize(pageWidth, pageHeight, pageUnit);
    builder.toStream(os);
    builder.run();
  }
}

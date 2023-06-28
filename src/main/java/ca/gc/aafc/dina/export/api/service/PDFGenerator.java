package ca.gc.aafc.dina.export.api.service;

import java.io.IOException;
import java.io.OutputStream;

public interface PDFGenerator {

  /**
   *
   * @param html
   * @param baseDocumentUri base uri to resolve relative paths
   * @param os
   * @throws IOException
   */
  void generatePDF(String html, String baseDocumentUri, OutputStream os) throws IOException;

}

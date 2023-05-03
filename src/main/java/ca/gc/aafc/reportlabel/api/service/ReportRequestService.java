package ca.gc.aafc.reportlabel.api.service;

import org.springframework.stereotype.Service;

import com.google.zxing.WriterException;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import ca.gc.aafc.reportlabel.api.config.ReportLabelConfig;
import ca.gc.aafc.reportlabel.api.config.ReportOutputFormat;
import ca.gc.aafc.reportlabel.api.dto.ReportRequestDto;
import ca.gc.aafc.reportlabel.api.entity.ReportTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main service to orchestrate report generation.
 */
@Service
public class ReportRequestService {

  private final Path workingFolder;
  private final ReportGenerator reportGenerator;
  private final PDFGenerator pdfGenerator;
  private final BarcodeGenerator barcodeGenerator;

  private final Configuration jacksonConfig;

  public ReportRequestService(
    ReportLabelConfig reportLabelConfig,
    FreemarkerReportGenerator reportGenerator,
                              OpenhtmltopdfGenerator pdfGenerator,
                              BarcodeGenerator barcodeGenerator) {
    this.reportGenerator = reportGenerator;
    this.pdfGenerator = pdfGenerator;
    this.barcodeGenerator = barcodeGenerator;

    workingFolder = Path.of(reportLabelConfig.getWorkingFolder());

    jacksonConfig = Configuration.builder()
      .mappingProvider( new JacksonMappingProvider() )
      .jsonProvider( new JacksonJsonProvider() )
      .build();
  }

  public ReportGenerationResult generateReport(ReportTemplate template, ReportRequestDto reportRequest) throws IOException {

    UUID uuid = UUID.randomUUID();
    Path tmpDirectory = Files.createDirectories(workingFolder.resolve(uuid.toString()));

    // Generate barcodes (if required by the template)
    if(template.getIncludesBarcode()) {
      BarcodeGenerator.CodeGenerationOption cgo = BarcodeGenerator.buildDefaultQrConfig();
      for(BarcodeSpecs currBarcodeSpecs : extractBarcodeSpecs(reportRequest.getPayload())) {
        File tempBarcodeFile =
          tmpDirectory.resolve(currBarcodeSpecs.id + "." + BarcodeGenerator.CODE_OUTPUT_FORMAT)
            .toFile();
        try (FileOutputStream fos = new FileOutputStream(tempBarcodeFile)) {
          try {
            barcodeGenerator.createCode(currBarcodeSpecs.content, fos, cgo);
          } catch (WriterException e) {
            throw new IOException(e);
          }
        }
      }
    }

    // only PDF is supported for now
    if(reportRequest.getOutputFormat() == ReportOutputFormat.PDF) {

      // Step 2 : Generate report as html
      File tempHtmlFile = tmpDirectory.resolve("report.html").toFile();
      try (FileWriter fw = new FileWriter(tempHtmlFile, StandardCharsets.UTF_8)) {
        reportGenerator.generateReport(template.getTemplateFilename(), reportRequest.getPayload(), fw);
      }

      // Step 3 : transform html to pdf
      File tempPdfFile = tmpDirectory.resolve(ReportLabelConfig.PDF_REPORT_FILENAME).toFile();
      try (FileOutputStream bos = new FileOutputStream(tempPdfFile)) {
        String htmlContent = Files.readString(tempHtmlFile.toPath(), StandardCharsets.UTF_8);
        pdfGenerator.generatePDF(htmlContent, tmpDirectory.toUri().toString(), bos);
      }
      return new ReportGenerationResult(uuid);
    }
    return null;
  }

  private List<BarcodeSpecs> extractBarcodeSpecs(Map<String, Object> payload) {
    DocumentContext dc = JsonPath.using(jacksonConfig).parse(payload);
    TypeRef<List<BarcodeSpecs>> typeRef = new TypeRef<>() {
    };
    try {
      return dc.read("$.elements[*].barcode", typeRef);
    } catch (PathNotFoundException pnf) {
      return List.of();
    }
  }

  /**
   * Result of the Report request.
   * @param resultIdentifier
   */
  public record ReportGenerationResult(UUID resultIdentifier) {

  }

  /**
   * Barcode generation required data
   * @param id the id used to generate the barcode file. Will be used as the filename.
   * @param content the output the barcode should give when scanned
   */
  record BarcodeSpecs(String id, String content) {

  }

}

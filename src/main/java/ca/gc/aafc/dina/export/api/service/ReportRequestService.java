package ca.gc.aafc.dina.export.api.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.WriterException;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import ca.gc.aafc.dina.export.api.config.ReportLabelConfig;
import ca.gc.aafc.dina.export.api.entity.ReportTemplate;
import ca.gc.aafc.dina.export.api.dto.ReportRequestDto;
import ca.gc.aafc.dina.export.api.file.FileController;
import ca.gc.aafc.dina.export.api.output.CsvOutput;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;

/**
 * Main service to orchestrate report generation.
 */
@Service
@Log4j2
public class ReportRequestService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
  };

  private final Path workingFolder;
  private final ReportGenerator reportGenerator;
  private final PDFGenerator pdfGenerator;
  private final BarcodeGenerator barcodeGenerator;

  private final ObjectMapper objectMapper;
  private final Configuration jacksonConfig;

  public ReportRequestService(
    ObjectMapper objectMapper,
    ReportLabelConfig reportLabelConfig, FreemarkerReportGenerator reportGenerator,
    OpenhtmltopdfGenerator pdfGenerator, BarcodeGenerator barcodeGenerator) {

    this.reportGenerator = reportGenerator;
    this.pdfGenerator = pdfGenerator;
    this.barcodeGenerator = barcodeGenerator;

    workingFolder = Path.of(reportLabelConfig.getWorkingFolder());

    this.objectMapper = objectMapper;
    jacksonConfig = Configuration.builder()
      .mappingProvider(new JacksonMappingProvider())
      .jsonProvider(new JacksonJsonProvider())
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
    
    // Generate a report based on template
    File templateOutputFile = null;
    String extension = FileController.getExtensionForMediaType(template.getTemplateOutputMediaType());
    if(StringUtils.isNotBlank(extension)) {
      templateOutputFile = tmpDirectory.resolve(ReportLabelConfig.REPORT_FILENAME + "." + extension).toFile();
      try (FileWriter fw = new FileWriter(templateOutputFile, StandardCharsets.UTF_8)) {
        reportGenerator.generateReport(template.getTemplateFilename(), reportRequest.getPayload(), fw);
      }
    } else {
      throw new IOException("No extension found for " + template.getTemplateOutputMediaType());
    }

    // sanity check
    if(!templateOutputFile.exists()) {
      throw new IOException("Report output not found.");
    }

    // If we need a PDF, transform the HTML to PDF
    if(MediaType.APPLICATION_PDF_VALUE.equals(template.getOutputMediaType())) {
      if(!MediaType.TEXT_HTML_VALUE.equals(template.getTemplateOutputMediaType())) {
        throw new IOException("No intermediate html file found");
      }
      generatePDF(tmpDirectory, templateOutputFile);
    } else if (ReportLabelConfig.TEXT_CSV_VALUE.equals(template.getOutputMediaType())) {
      if(!MediaType.APPLICATION_JSON_VALUE.equals(template.getTemplateOutputMediaType())) {
        throw new IOException("No intermediate json file found");
      }
      generateCSV(tmpDirectory, templateOutputFile);
    }
    return new ReportGenerationResult(uuid);
  }

  /**
   * Generates a PDF from an HTML source.
   * @param tmpDirectory path where to store the PDF
   * @param htmlFile the transitory html file to be used to generate the PDF
   * @throws IOException
   */
  private void generatePDF(Path tmpDirectory, File htmlFile) throws IOException {
    Objects.requireNonNull(htmlFile);
    File tempPdfFile = tmpDirectory.resolve(ReportLabelConfig.PDF_REPORT_FILENAME).toFile();
    try (FileOutputStream bos = new FileOutputStream(tempPdfFile)) {
      String htmlContent = Files.readString(htmlFile.toPath(), StandardCharsets.UTF_8);
      pdfGenerator.generatePDF(htmlContent, tmpDirectory.toUri().toString(), bos);
    }
    if (!htmlFile.delete()) {
      log.warn("can't delete intermediate file " + htmlFile.getAbsolutePath());
    }
  }

  /**
   * Generates a CSV from a JSON source.
   * @param tmpDirectory path where to store the CSV
   * @param jsonFile the transitory json file to be used to generate the CSV
   * @throws IOException
   */
  private void generateCSV(Path tmpDirectory, File jsonFile) throws IOException {
    Objects.requireNonNull(jsonFile);
    File csvFile = tmpDirectory.resolve(ReportLabelConfig.CSV_REPORT_FILENAME).toFile();

    // Read json file
    Map<String, Object> jsonAsMap = objectMapper.readValue(jsonFile, MAP_TYPE_REF);

    // Make sure the structure is as expected
    if (!jsonAsMap.containsKey(ReportLabelConfig.PAYLOAD_KEY) ||
      !(jsonAsMap.get(ReportLabelConfig.PAYLOAD_KEY) instanceof List)) {
      throw new IOException("Can't find payload element");
    }

    List<Map<String, Object>> payload = (List<Map<String, Object>>) jsonAsMap.get(ReportLabelConfig.PAYLOAD_KEY);

    // Base the headers on the first record
    List<String> headers = payload.isEmpty() ? List.of() : List.copyOf(payload.get(0).keySet());
    try (Writer w = new FileWriter(csvFile);
         CsvOutput<Map<String, Object>> output =
           CsvOutput.create(headers, MAP_TYPE_REF, w)) {
      for (Map<String, Object> line : payload) {
        output.addRow(line);
      }
    }

    if (!jsonFile.delete()) {
      log.warn("can't delete intermediate file " + jsonFile.getAbsolutePath());
    }
  }

  /**
   * Extracts barcode specification from the payload.
   * @param payload
   * @return
   */
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

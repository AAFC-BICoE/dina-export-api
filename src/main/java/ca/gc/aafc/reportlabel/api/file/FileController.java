package ca.gc.aafc.reportlabel.api.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.gc.aafc.reportlabel.api.config.ReportLabelConfig;

@RestController
@RequestMapping("/api/v1")
@Log4j2
public class FileController {

  private final Path workingFolder;

  public FileController(ReportLabelConfig config) {
    this.workingFolder = Path.of(config.getWorkingFolder());
  }

  @GetMapping("/file/{fileId}")
  public ResponseEntity<InputStreamResource> downloadReport(@PathVariable UUID fileId) throws IOException {

    Path reportFile =
      workingFolder.resolve(fileId.toString()).resolve(ReportLabelConfig.PDF_REPORT_FILENAME);

    InputStream fis = Files.newInputStream(reportFile);
    return new ResponseEntity<>(new InputStreamResource(fis),
      buildHttpHeaders(fileId.toString() + ".pdf", MediaType.APPLICATION_PDF, reportFile.toFile().length()), HttpStatus.OK);
  }

  /**
   * Utility method to generate HttpHeaders based on the given parameters.
   *
   * @param filename      name of the file
   * @param contentLength length of the file
   * @return HttpHeaders based on the given parameters
   */
  private static HttpHeaders buildHttpHeaders(String filename, MediaType mediaType, long contentLength) {
    HttpHeaders respHeaders = new HttpHeaders();
    respHeaders.setContentType(mediaType);
    respHeaders.setContentLength(contentLength);
    respHeaders.setContentDispositionFormData("attachment", filename);
    return respHeaders;
  }
}

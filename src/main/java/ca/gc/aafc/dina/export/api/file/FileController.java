package ca.gc.aafc.dina.export.api.file;

import io.crnk.core.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.gc.aafc.dina.export.api.config.ReportLabelConfig;

@RestController
@RequestMapping("/api/v1")
@Log4j2
public class FileController {

  private static final TikaConfig TIKA_CONFIG = TikaConfig.getDefaultConfig();

  private final Path workingFolder;

  public FileController(ReportLabelConfig config) {
    this.workingFolder = Path.of(config.getWorkingFolder());
  }

  @GetMapping("/file/{fileId}")
  public ResponseEntity<InputStreamResource> downloadReport(@PathVariable UUID fileId) throws IOException {

    Path reportFolder =
      workingFolder.resolve(fileId.toString());

    Optional<Path> possibleReportPath;
    try (Stream<Path> walk = Files.walk(reportFolder, 1)) {
      possibleReportPath = walk
        .filter(p-> p.getFileName().toString().startsWith(ReportLabelConfig.REPORT_FILENAME))
        .findFirst();
    }

    if(possibleReportPath.isPresent()) {
      Path reportPath = possibleReportPath.get();
      String filename = Objects.toString(reportPath.getFileName(), "");

      InputStream fis = Files.newInputStream(reportPath);
      MediaType md = MediaType.parseMediaType(getMediaTypeForFilename(filename).toString());
      return new ResponseEntity<>(new InputStreamResource(fis),
        buildHttpHeaders(fileId + "." + StringUtils.substringAfterLast(filename, "."), md,
          reportPath.toFile().length()), HttpStatus.OK);
    }
    throw new ResourceNotFoundException("Report with ID " + fileId + " Not Found.");
  }

  /**
   * Utility method to get the most common file extension for a media type.
   * @param mediaType
   * @return
   */
  public static String getExtensionForMediaType(String mediaType) {
    MimeType mimeType;
    try {
      mimeType = TIKA_CONFIG.getMimeRepository().forName(mediaType);
    } catch (MimeTypeException e) {
      return null;
    }
    return mimeType.getExtension();
  }

  /**
   * Try to get the MediaType (Tika MediaType) from the filename with extension.
   * If not possible Octet Stream is returned.
   * @param filename
   * @return
   */
  public org.apache.tika.mime.MediaType getMediaTypeForFilename(String filename) {
    Metadata metadata = new Metadata();
    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
    try {
      return TIKA_CONFIG.getDetector().detect(null, metadata);
    } catch (IOException e) {
      // ignore
    }
    return org.apache.tika.mime.MediaType.OCTET_STREAM;
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

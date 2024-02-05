package ca.gc.aafc.dina.export.api.file;

import io.crnk.core.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.persistence.NoResultException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;

import static ca.gc.aafc.dina.export.api.generator.TabularDataExportGenerator.DATA_EXPORT_CSV_FILENAME;

@RestController
@RequestMapping("/api/v1")
@Log4j2
public class FileController {

  public enum DownloadType { LABEL, DATA_EXPORT }
  private static final TikaConfig TIKA_CONFIG = TikaConfig.getDefaultConfig();

  private final DataExportStatusService dataExportStatusService;
  private final Path labelWorkingFolder;
  private final Path dataExportWorkingFolder;

  public FileController(DataExportConfig dataExportConfig, DataExportStatusService dataExportStatusService) {
    this.labelWorkingFolder = dataExportConfig.getGeneratedReportsLabelsPath();
    this.dataExportWorkingFolder = dataExportConfig.getGeneratedDataExportsPath();
    this.dataExportStatusService = dataExportStatusService;
  }

  @GetMapping("/file/{fileId}")
  public ResponseEntity<InputStreamResource> downloadFile(@PathVariable UUID fileId,
                                                            @RequestParam( name = "type", required = false) DownloadType type) throws IOException {

    Optional<Path> filePath = Optional.empty();

    if (type == null || type == DownloadType.LABEL) {
      Path reportFolder =
        labelWorkingFolder.resolve(fileId.toString());
      try (Stream<Path> walk = Files.walk(reportFolder, 1)) {
        filePath = walk
          .filter(p -> p.getFileName().toString().startsWith(DataExportConfig.REPORT_FILENAME))
          .findFirst();
      }
    } else if (type == DownloadType.DATA_EXPORT) {
      // make sure the export is completed
      try {
        if (DataExport.ExportStatus.COMPLETED == dataExportStatusService.findStatus(fileId)) {

          Path csvPath = dataExportWorkingFolder.resolve(fileId.toString()).resolve(DATA_EXPORT_CSV_FILENAME);

          if(csvPath.toFile().exists()) {
            filePath = Optional.of(
              dataExportWorkingFolder.resolve(fileId.toString()).resolve(DATA_EXPORT_CSV_FILENAME));
          } else {
            // try to find a file matching that uuid
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataExportWorkingFolder,
              fileId + ".*")) {
              // Print all the files to output stream
              for (Path p : stream) {
                filePath = Optional.of(p);
              }
            }
          }
        }
      } catch (NoResultException ignored) {
        // nothing to do since filePath will remain empty
      }
    }

    if(filePath.isPresent()) {
      return downloadFile(fileId, filePath.get());
    }
    throw new ResourceNotFoundException("Report with ID " + fileId + " Not Found.");
  }

  private ResponseEntity<InputStreamResource> downloadFile(UUID fileIdentifier, Path filePath) throws IOException {

    String filename = Objects.toString(filePath.getFileName(), "");

    InputStream fis = Files.newInputStream(filePath);
    MediaType md = MediaType.parseMediaType(getMediaTypeForFilename(filename).toString());
    return new ResponseEntity<>(new InputStreamResource(fis),
      buildHttpHeaders(fileIdentifier + "." + StringUtils.substringAfterLast(filename, "."), md,
        filePath.toFile().length()), HttpStatus.OK);
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

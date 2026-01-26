package ca.gc.aafc.dina.export.api.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
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
import org.springframework.web.server.ResponseStatusException;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.service.DataExportService;
import ca.gc.aafc.dina.export.api.service.TransactionWrapper;

@RestController
@RequestMapping("/api/v1")
@Log4j2
public class FileController {

  // that regex will also remove accentuated characters
  private static final Pattern FILENAME_REGEX = Pattern.compile("[^a-zA-Z0-9_-]");

  public enum DownloadType { LABEL, DATA_EXPORT }
  private static final TikaConfig TIKA_CONFIG = TikaConfig.getDefaultConfig();

  private final DataExportService dataExportService;
  private final Path labelWorkingFolder;
  private final Path dataExportWorkingFolder;
  private final TransactionWrapper transactionWrapper;

  public FileController(DataExportConfig dataExportConfig, DataExportService dataExportService,
                        TransactionWrapper transactionWrapper) {
    this.labelWorkingFolder = dataExportConfig.getGeneratedReportsLabelsPath();
    this.dataExportWorkingFolder = dataExportConfig.getGeneratedDataExportsPath();
    this.dataExportService = dataExportService;
    this.transactionWrapper = transactionWrapper;
  }

  @GetMapping("/file/{fileId}")
  public ResponseEntity<InputStreamResource> downloadFile(@PathVariable UUID fileId,
                                                          @RequestParam( name = "type", required = false) DownloadType type) throws IOException {
    Optional<Path> filePath = Optional.empty();
    String customFilename = null;

    if (type == null || type == DownloadType.LABEL) {
      Path reportFolder =
        labelWorkingFolder.resolve(fileId.toString());
      try (Stream<Path> walk = Files.walk(reportFolder, 1)) {
        filePath = walk
          .filter(p -> p.getFileName().toString().startsWith(DataExportConfig.REPORT_FILENAME))
          .findFirst();
      }
    } else if (type == DownloadType.DATA_EXPORT) {
      DataExport exportEntity = transactionWrapper
        .runInsideReadTransaction( () -> dataExportService.findOne(fileId));
      // make sure the export is completed
      try {
        if (DataExport.ExportStatus.COMPLETED == exportEntity.getStatus()) {
          customFilename = exportEntity.getName();
          filePath = getExportFileLocation(fileId, exportEntity.getFilename());
        }
      } catch (NoResultException ignored) {
        // nothing to do since filePath will remain empty
      }
    }

    if (filePath.isPresent()) {
      return downloadFile(fileId, filePath.get(), customFilename);
    }
    throw buildNotFoundException("DataExport or Report with ID " + fileId + " Not Found.");
  }

  /**
   * Get location, on disk, of an export file.
   * @param fileId
   * @param filename
   * @return Optional with the Path if found or empty if not
   */
  public Optional<Path> getExportFileLocation(UUID fileId, String filename) {

    if (fileId == null || StringUtils.isBlank(filename)) {
      return Optional.empty();
    }

    Path tabularFilePath = dataExportWorkingFolder.resolve(fileId.toString()).resolve(filename);
    if (tabularFilePath.toFile().exists()) {
      return Optional.of(tabularFilePath);
    } else {
      // try to find a file matching that uuid
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataExportWorkingFolder,
        fileId + ".*")) {
        // we should only have one returned
        for (Path p : stream) {
          return Optional.of(p);
        }
      } catch (IOException e) {
        log.error(e);
      }
    }
    return Optional.empty();
  }

  /**
   * Inner function to handle file download.
   * @param fileIdentifier
   * @param filePath
   * @param customFilename optional, filename to return in the http response
   * @return
   */
  private ResponseEntity<InputStreamResource> downloadFile(UUID fileIdentifier, Path filePath, String customFilename) throws IOException {

    String filename = Objects.toString(filePath.getFileName(), "");
    String downloadFilename = StringUtils.defaultString(customFilename, fileIdentifier.toString());

    // make sure the filename is alphanumeric
    downloadFilename = FILENAME_REGEX.matcher(downloadFilename).replaceAll("_");

    InputStream fis = Files.newInputStream(filePath);
    MediaType md = MediaType.parseMediaType(getMediaTypeForFilename(filename).toString());
    return new ResponseEntity<>(new InputStreamResource(fis),
      buildHttpHeaders(downloadFilename + "." + StringUtils.substringAfterLast(filename, "."), md,
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

  /**
   * Utility method to generate a NOT_FOUND ResponseStatusException based on the given parameters.
   *
   * @param message not found message
   * @return a ResponseStatusException Not found
   */
  private ResponseStatusException buildNotFoundException(String message) {
    return new ResponseStatusException(
      HttpStatus.NOT_FOUND, message, null);
  }
}

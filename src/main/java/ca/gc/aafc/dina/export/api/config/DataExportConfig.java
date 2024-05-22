package ca.gc.aafc.dina.export.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;

import ca.gc.aafc.dina.export.api.entity.DataExport;

import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.inject.Named;
import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

/**
 * Configured on DinaExportModuleApiLauncher
 */
@ConfigurationProperties(prefix = "dina.export")
@Getter
@Setter
@NoArgsConstructor
@Validated
@Named("dataExportConfig")
public class DataExportConfig {

  public static final String DINA_THREAD_POOL_BEAN_NAME = "DinaThreadPoolTaskExecutor";

  public static final String GENERATED_REPORTS_LABELS = "generated_reports_labels";
  public static final String GENERATED_DATA_EXPORTS = "generated_data_exports";

  // represents the payload section of the JSON used for the report
  public static final String PAYLOAD_KEY = "payload";

  public static final String TEXT_CSV_VALUE = MediaType.parseMediaType("text/csv").toString();
  public static final String DATA_EXPORT_CSV_FILENAME = "export.csv";

  public static final String ZIP_EXT = "zip";

  public static final String PDF_REPORT_FILENAME = "report.pdf";
  public static final String CSV_REPORT_FILENAME = "report.csv";
  public static final String REPORT_FILENAME = "report";
  public static final String TEMP_HTML = "report_1.html";

  public static final String OBJECT_STORE_TOA = "toa";
  public static final String OBJECT_STORE_SOURCE = "object-store";

  @NotBlank
  private String workingFolder;

  private Integer elasticSearchPageSize;

  private String objectStoreDownloadUrl;

  // default to DISABLED
  private String expiredExportCronExpression = Scheduled.CRON_DISABLED;

  @DurationUnit(ChronoUnit.SECONDS)
  private Duration expiredExportMaxAge;

  public Path getGeneratedReportsLabelsPath() {
    return Path.of(workingFolder).resolve(GENERATED_REPORTS_LABELS);
  }

  public Path getGeneratedDataExportsPath() {
    return Path.of(workingFolder).resolve(GENERATED_DATA_EXPORTS);
  }

  public Path getPathForDataExport(DataExport dataExport) {

    Path path = isExportTypeUsesDirectory(dataExport.getExportType()) ?
      getGeneratedDataExportsPath().resolve(dataExport.getUuid().toString()) :
      getGeneratedDataExportsPath();

    return switch (dataExport.getExportType()) {
      case TABULAR_DATA -> path.resolve(DATA_EXPORT_CSV_FILENAME);
      case OBJECT_ARCHIVE -> path.resolve(dataExport.getUuid().toString() + "." + ZIP_EXT);
    };
  }

  /**
   * Is the export type uses a directory to store the export or is it storing it in the main
   * directory ?
   * @param type
   * @return
   */
  public static boolean isExportTypeUsesDirectory(DataExport.ExportType type) {
    return switch (type) {
      case TABULAR_DATA -> true;
      case OBJECT_ARCHIVE -> false;
    };
  }

  /**
   * Checks if the directory is a data export directory (matching the uuid of the data export)
   * @param directory
   * @param dataExport
   * @return
   */
  public static boolean isDataExportDirectory(Path directory, @NonNull DataExport dataExport) {

    if (directory == null) {
      return false;
    }

    Path dirFile = directory.getFileName();
    if (dirFile == null) {
      return false;
    }

    UUID exportId = dataExport.getUuid();
    if(exportId == null) {
       throw new IllegalArgumentException("DataExport UUID can't be null");
    }

    return dirFile.toString().equals(exportId.toString());
  }

}

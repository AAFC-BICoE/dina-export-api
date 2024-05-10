package ca.gc.aafc.dina.export.api.config;

import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import javax.inject.Named;
import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;

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

  public static final String PDF_REPORT_FILENAME = "report.pdf";
  public static final String CSV_REPORT_FILENAME = "report.csv";
  public static final String REPORT_FILENAME = "report";
  public static final String TEMP_HTML = "report_1.html";

  public static final String OBJECT_STORE_TOA = "toa";
  public static final String OBJECT_STORE_SOURCE = "object-store";

  @NotBlank
  private String workingFolder;

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

}

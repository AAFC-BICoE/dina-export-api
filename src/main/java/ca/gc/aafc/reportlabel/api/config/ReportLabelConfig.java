package ca.gc.aafc.reportlabel.api.config;

import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "report-label")
@Getter
@Setter
@NoArgsConstructor
@Validated
public class ReportLabelConfig {

  public static final String PDF_REPORT_FILENAME = "report.pdf";

  @NotBlank
  private String workingFolder;

}
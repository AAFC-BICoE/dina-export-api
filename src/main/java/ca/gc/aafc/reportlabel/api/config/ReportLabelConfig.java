package ca.gc.aafc.reportlabel.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "report-label")
@Getter
@Setter
@NoArgsConstructor
public class ReportLabelConfig {

  private String workingDir;

}

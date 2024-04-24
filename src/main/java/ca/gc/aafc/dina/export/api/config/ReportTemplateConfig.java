package ca.gc.aafc.dina.export.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configured on DinaExportModuleApiLauncher
 */
@ConfigurationProperties(prefix = "dina.report")
@Getter
@Setter
@NoArgsConstructor
public class ReportTemplateConfig {

  private String templateFolder;

}

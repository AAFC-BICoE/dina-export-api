package ca.gc.aafc.dina.export.api.config;

import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configured on DinaExportModuleApiLauncher
 */
@ConfigurationProperties(prefix = "data-export")
@Getter
@Setter
@NoArgsConstructor
@Validated
public class DataExportConfig {

  @NotBlank
  private String workingFolder;

}

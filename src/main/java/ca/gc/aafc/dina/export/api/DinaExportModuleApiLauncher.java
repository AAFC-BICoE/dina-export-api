package ca.gc.aafc.dina.export.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import ca.gc.aafc.dina.export.api.config.ReportLabelConfig;


/**
 * Launches the application.
 */
// CHECKSTYLE:OFF HideUtilityClassConstructor (Configuration class can not have
// invisible constructor, ignore the check style error for this case)

@SpringBootApplication
@EnableConfigurationProperties(value = ReportLabelConfig.class)
public class DinaExportModuleApiLauncher {
  public static void main(String[] args) {
    SpringApplication.run(DinaExportModuleApiLauncher.class, args);
  }
}
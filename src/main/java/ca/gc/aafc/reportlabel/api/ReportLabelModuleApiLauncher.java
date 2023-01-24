package ca.gc.aafc.reportlabel.api;

import org.javers.spring.boot.sql.JaversSqlAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Launches the application.
 */
// CHECKSTYLE:OFF HideUtilityClassConstructor (Configuration class can not have
// invisible constructor, ignore the check style error for this case)

// exclude all database related auto-configuration
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, JaversSqlAutoConfiguration.class})
public class ReportLabelModuleApiLauncher {
  public static void main(String[] args) {
    SpringApplication.run(ReportLabelModuleApiLauncher.class, args);
  }
}

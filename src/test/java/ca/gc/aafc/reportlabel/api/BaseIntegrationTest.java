package ca.gc.aafc.reportlabel.api;

import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Properties;

@SpringBootTest(classes = {BaseIntegrationTest.TestConfig.class, ReportLabelModuleApiLauncher.class })
public class BaseIntegrationTest {

  @TestConfiguration
  public static class TestConfig {
    @Bean
    public BuildProperties buildProperties() {
      Properties props = new Properties();
      props.setProperty("version", "test-api-version");
      return new BuildProperties(props);
    }
  }
}

package ca.gc.aafc.dina.export.api;

import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import ca.gc.aafc.dina.export.api.async.AsyncConsumer;
import ca.gc.aafc.dina.testsupport.PostgresTestContainerInitializer;

@SpringBootTest(classes = {BaseIntegrationTest.TestConfig.class, DinaExportModuleApiLauncher.class })
@TestPropertySource(properties = "spring.config.additional-location=classpath:application-test.yml")
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
public class BaseIntegrationTest {

  @TestConfiguration
  public static class TestConfig {
    @Bean
    public BuildProperties buildProperties() {
      Properties props = new Properties();
      props.setProperty("version", "test-api-version");
      return new BuildProperties(props);
    }

    @Bean
    public AsyncConsumer<Future<UUID>> futureConsumer() {
      return new AsyncConsumer<>();
    }
  }


}

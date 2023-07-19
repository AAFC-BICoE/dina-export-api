package ca.gc.aafc.dina.export.api;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import ca.gc.aafc.dina.testsupport.elasticsearch.ElasticSearchTestUtils;

public class ElasticSearchTestContainerInitializer implements
  ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static ElasticsearchContainer esContainer = null;

  @Override
  public void initialize(ConfigurableApplicationContext ctx) {
    ConfigurableEnvironment env = ctx.getEnvironment();

    if (esContainer == null) {
      esContainer = new ElasticsearchContainer(ElasticSearchTestUtils.ES_IMAGE);
      esContainer.start();
    }

    TestPropertyValues.of(
      "elasticsearch.host=" + esContainer.getHost()
    ).applyTo(env);
    TestPropertyValues.of(
      "elasticsearch.port=" + esContainer.getMappedPort(9200).toString()
    ).applyTo(env);
  }

}
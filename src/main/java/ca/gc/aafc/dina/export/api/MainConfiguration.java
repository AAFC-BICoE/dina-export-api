package ca.gc.aafc.dina.export.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toedter.spring.hateoas.jsonapi.JsonApiConfiguration;

import ca.gc.aafc.dina.DinaBaseApiAutoConfiguration;
import ca.gc.aafc.dina.service.JaversDataService;

import static ca.gc.aafc.dina.export.api.config.DataExportConfig.DINA_THREAD_POOL_BEAN_NAME;

import java.util.concurrent.Executor;

@Configuration
@ComponentScan(basePackageClasses = DinaBaseApiAutoConfiguration.class)
@ImportAutoConfiguration(DinaBaseApiAutoConfiguration.class)
@MapperScan(basePackageClasses = JaversDataService.class)
@EnableAsync
@EnableScheduling
public class MainConfiguration {

  @Bean(name = DINA_THREAD_POOL_BEAN_NAME)
  @ConditionalOnMissingBean
  public Executor threadPoolTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(15);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("AsyncExecutor-");
    executor.initialize();
    return executor;
  }

  @Bean
  public JsonApiConfiguration jsonApiConfiguration() {
    return new JsonApiConfiguration()
      .withPluralizedTypeRendered(false)
      .withPageMetaAutomaticallyCreated(false)
      .withObjectMapperCustomizer(objectMapper -> {
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.registerModule(new JavaTimeModule());
      });
  }

}

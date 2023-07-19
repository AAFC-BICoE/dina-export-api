package ca.gc.aafc.dina.export.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

@Configuration
public class JsonPathConfiguration {

  @Bean
  public com.jayway.jsonpath.Configuration provideJsonPathConfiguration() {
    return com.jayway.jsonpath.Configuration.builder()
      .mappingProvider(new JacksonMappingProvider())
      .jsonProvider(new JacksonJsonProvider())
      .build();
  }
}

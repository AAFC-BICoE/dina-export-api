package ca.gc.aafc.dina.export.api.config;

import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import ca.gc.aafc.dina.client.config.OpenIdConnectConfig;

@ConfigurationProperties(prefix = "http-client")
@NoArgsConstructor  
@Data
@EqualsAndHashCode(callSuper = true)
public class HttpClientConfig extends OpenIdConnectConfig {

  private Set<String> allowedHosts;

}

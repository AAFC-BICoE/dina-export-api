package ca.gc.aafc.dina.export.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import ca.gc.aafc.dina.client.config.OpenIdConnectConfig;

@ConfigurationProperties(prefix = "http-client")
@Getter
@Setter
@NoArgsConstructor
public class HttpClientConfig extends OpenIdConnectConfig {

}

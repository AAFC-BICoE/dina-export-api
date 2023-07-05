package ca.gc.aafc.dina.export.api;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.gc.aafc.dina.security.DinaAuthenticatedUser;
import ca.gc.aafc.dina.security.DinaRole;

@Configuration
@ConditionalOnProperty(value = "keycloak.enabled", havingValue = "false")
public class DinaAuthenticatedUserConfig {

  public static final String USER_NAME = "test_user";
  public static final String TEST_BUCKET = "test";
  public static final Map<String, Set<DinaRole>> ROLES_PER_GROUPS =
    Map.of(
      TEST_BUCKET, Collections.singleton(DinaRole.GUEST),
      "Group 2", Collections.singleton(DinaRole.GUEST)
    );

  @Bean
  public DinaAuthenticatedUser dinaAuthenticatedUser() {
    return DinaAuthenticatedUser.builder()
      .username(USER_NAME)
      .rolesPerGroup(ROLES_PER_GROUPS)
      .build();
  }
}

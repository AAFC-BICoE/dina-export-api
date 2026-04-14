package ca.gc.aafc.dina.export.api.config;

import javax.inject.Named;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.messaging.config.RabbitMQQueueProperties;

@ConfigurationProperties(prefix = "dina.messaging.user")
@Component
@Named("userNotificationQueueProperties")
public class UserNotificationQueueProperties extends RabbitMQQueueProperties {

  public enum NotificationType {OBJECT_EXPORT_READY}

}


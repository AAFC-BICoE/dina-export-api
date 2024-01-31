package ca.gc.aafc.dina.export.api.config;

import javax.inject.Named;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Configuration;

import ca.gc.aafc.dina.messaging.config.RabbitMQConsumerConfiguration;
import ca.gc.aafc.dina.messaging.config.RabbitMQQueueProperties;

@Configuration
@ConditionalOnProperty(prefix = "dina.messaging", name = "isConsumer", havingValue = "true")
public class ObjectExportQueueConsumerConfiguration extends RabbitMQConsumerConfiguration {

  public ObjectExportQueueConsumerConfiguration(@Named("exportQueueProperties")
                                                RabbitMQQueueProperties queueProperties) {
    super(queueProperties);
  }

  @Bean("exportQueue")
  @Override
  public Queue createQueue() {
    return super.createQueue();
  }

  @Bean("exportDeadLetterQueue")
  @Override
  public Queue createDeadLetterQueue() {
    return super.createDeadLetterQueue();
  }
}

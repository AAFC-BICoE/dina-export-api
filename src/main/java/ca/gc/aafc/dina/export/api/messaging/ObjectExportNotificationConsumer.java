package ca.gc.aafc.dina.export.api.messaging;

import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.messaging.config.RabbitMQQueueProperties;
import ca.gc.aafc.dina.messaging.consumer.RabbitMQMessageConsumer;
import ca.gc.aafc.dina.messaging.message.ObjectExportNotification;

@Log4j2
@Service
@ConditionalOnProperty(prefix = "dina.messaging", name = "isConsumer", havingValue = "true")
public class ObjectExportNotificationConsumer implements RabbitMQMessageConsumer<ObjectExportNotification> {

  /**
   * Constructor
   * @param queueProperties not used directly, but we take it to make sure we have it available for receiveMessage method
   */
  public ObjectExportNotificationConsumer(@Named("exportQueueProperties") RabbitMQQueueProperties queueProperties) {

  }

  @RabbitListener(queues = "#{exportQueueProperties.getQueue()}")
  @Override
  public void receiveMessage(ObjectExportNotification objectExportNotification) {
    log.info("Received message and deserialized to : {}", objectExportNotification::toString);
  }
}

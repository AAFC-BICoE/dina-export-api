package ca.gc.aafc.dina.export.api.messaging;

import java.util.Map;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.service.DataExportService;
import ca.gc.aafc.dina.messaging.config.RabbitMQQueueProperties;
import ca.gc.aafc.dina.messaging.consumer.RabbitMQMessageConsumer;
import ca.gc.aafc.dina.messaging.message.ObjectExportNotification;

@Log4j2
@Service
@ConditionalOnProperty(prefix = "dina.messaging", name = "isConsumer", havingValue = "true")
public class ObjectExportNotificationConsumer implements RabbitMQMessageConsumer<ObjectExportNotification> {

  private final DataExportService dataExportService;

  /**
   * Constructor
   * @param queueProperties not used directly, but we take it to make sure we have it available for receiveMessage method
   */
  public ObjectExportNotificationConsumer(@Named("exportQueueProperties") RabbitMQQueueProperties queueProperties,
                                          DataExportService dataExportService) {
    this.dataExportService = dataExportService;
  }

  @RabbitListener(queues = "#{exportQueueProperties.getQueue()}")
  @Override
  public void receiveMessage(ObjectExportNotification objectExportNotification) {
    log.info("Received message and deserialized to : {}", objectExportNotification::toString);

    DataExport dataExport = new DataExport();
    dataExport.setUuid(objectExportNotification.getUuid());
    dataExport.setExportType(DataExport.ExportType.OBJECT_ARCHIVE);
    dataExport.setCreatedBy(objectExportNotification.getUsername());
    dataExport.setSource(DataExportConfig.OBJECT_STORE_SOURCE);
    dataExport.setTransitiveData(Map.of(DataExportConfig.OBJECT_STORE_TOA, objectExportNotification.getToa()));

    dataExportService.create(dataExport);
  }
}

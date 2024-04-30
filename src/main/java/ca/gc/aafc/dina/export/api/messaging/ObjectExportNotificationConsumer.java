package ca.gc.aafc.dina.export.api.messaging;

import java.io.IOException;
import java.util.Map;
import javax.inject.Named;
import javax.transaction.Transactional;
import lombok.extern.log4j.Log4j2;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.service.DataExportService;
import ca.gc.aafc.dina.export.api.service.ReportTemplateFileService;
import ca.gc.aafc.dina.messaging.config.RabbitMQQueueProperties;
import ca.gc.aafc.dina.messaging.message.ObjectExportNotification;
import ca.gc.aafc.dina.messaging.message.ReportTemplateUploadNotification;

@Log4j2
@Service
@RabbitListener(queues = "#{exportQueueProperties.getQueue()}")
@ConditionalOnProperty(prefix = "dina.messaging", name = "isConsumer", havingValue = "true")
public class ObjectExportNotificationConsumer {

  private final DataExportService dataExportService;
  private final ReportTemplateFileService reportTemplateFileService;

  /**
   * Constructor
   * @param queueProperties not used directly, but we take it to make sure we have it available for receiveMessage method
   */
  public ObjectExportNotificationConsumer(@Named("exportQueueProperties") RabbitMQQueueProperties queueProperties,
                                          DataExportService dataExportService, ReportTemplateFileService reportTemplateFileService) {
    this.dataExportService = dataExportService;
    this.reportTemplateFileService = reportTemplateFileService;
  }

  @RabbitHandler
  @Transactional
  public void handleObjectExportNotification(ObjectExportNotification objectExportNotification) {
    log.info("Received message and deserialized to : {}", objectExportNotification::toString);

    DataExport dataExport = new DataExport();
    dataExport.setUuid(objectExportNotification.getUuid());
    dataExport.setExportType(DataExport.ExportType.OBJECT_ARCHIVE);
    dataExport.setCreatedBy(objectExportNotification.getUsername());
    dataExport.setSource(DataExportConfig.OBJECT_STORE_SOURCE);
    dataExport.setTransitiveData(Map.of(DataExportConfig.OBJECT_STORE_TOA, objectExportNotification.getToa()));
    dataExport.setName(objectExportNotification.getName());

    dataExportService.create(dataExport);
  }

  @RabbitHandler
  public void handleReportTemplateUploadNotification(
    ReportTemplateUploadNotification reportTemplateUploadNotification) throws IOException {
    log.info("Received message and deserialized to : {}",
      reportTemplateUploadNotification::toString);
    reportTemplateFileService.downloadTemplate(reportTemplateUploadNotification.getToa());
  }
}

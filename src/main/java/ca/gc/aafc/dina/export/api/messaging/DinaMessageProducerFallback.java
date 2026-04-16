package ca.gc.aafc.dina.export.api.messaging;

import lombok.extern.log4j.Log4j2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.messaging.DinaMessage;
import ca.gc.aafc.dina.messaging.producer.DinaMessageProducer;

/**
 * Log4j2 based {@link DinaMessageProducer} used mostly to run in dev mode.
 * Used with @ConditionalOnMissingBean as a fallback.
 */
@Log4j2
@ConditionalOnMissingBean(RabbitMQDinaMessageProducer.class)
@Service
public class DinaMessageProducerFallback implements DinaMessageProducer {

  public DinaMessageProducerFallback() {
    log.warn("Using DinaMessageProducerFallback");
  }

  public void send(DinaMessage notification) {
    log.info("Message produced : {}", notification::toString);
  }
}

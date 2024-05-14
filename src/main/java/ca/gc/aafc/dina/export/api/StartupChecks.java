package ca.gc.aafc.dina.export.api;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.export.api.config.ReportTemplateConfig;

import freemarker.template.Configuration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
public class StartupChecks {

  @Inject
  private ReportTemplateConfig reportTemplateConfig;

  @Inject
  private Configuration freemarkerConfiguration;

  @EventListener(ApplicationReadyEvent.class)
  public void onAppReady() {
    log.info("Template Folder:" + reportTemplateConfig.getTemplateFolder());

    Path path = Path.of(reportTemplateConfig.getTemplateFolder());
    boolean templateFolderExists = Files.exists(path);
    log.info("Template Folder exists?:" + templateFolderExists);

    // make sure the destination folder exists
    if(!templateFolderExists) {
      log.info("Trying to create " + reportTemplateConfig.getTemplateFolder());
      try {
        Files.createDirectories(path);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      log.info("Template Folder created");
    }
    log.info("Template Folder writable?:" + Files.isWritable(path));

  }
}

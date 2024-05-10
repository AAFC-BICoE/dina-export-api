package ca.gc.aafc.dina.export.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.DinaExportModuleApiLauncher;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.testsupport.DatabaseSupportService;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.inject.Inject;

@SpringBootTest(classes = DinaExportModuleApiLauncher.class,
  properties = {
    "dina.export.expiredExportMaxAge=12d",
    "dina.export.expiredExportCronExpression=*/1 * * * * *"
  })
public class ExpiredExportRemovalServiceIT extends BaseIntegrationTest {

  public static final String INTERVAL_2_WEEKS = "UPDATE data_export SET created_on = created_on - interval '2 weeks'";

  @Inject
  protected DataExportConfig dataExportConfig;

  @Inject
  protected DatabaseSupportService dbSupportService;


  @Test
  public void a() throws IOException, InterruptedException {

    UUID testUUID = UUID.randomUUID();

    // make sure the destination folder exists
    if(!dataExportConfig.getGeneratedDataExportsPath().toFile().exists()) {
      Files.createDirectories(dataExportConfig.getGeneratedDataExportsPath());
    }
    Path p = dataExportConfig.getGeneratedDataExportsPath().resolve(testUUID + ".tmp");
    Files.writeString(p, "test text content");

    dbSupportService.runInNewTransaction(em -> {
      DataExport upload = DataExport.builder()
        .uuid(testUUID)
        .createdBy("test")
        .source("unit test")
        .exportType(DataExport.ExportType.TABULAR_DATA)
        .status(DataExport.ExportStatus.COMPLETED)
        .build();

      em.persist(upload);
      em.createNativeQuery(INTERVAL_2_WEEKS).executeUpdate(); // Mock record created in the past
      em.flush();
    });

    // wait for the Scheduled job to run
    boolean fileDeleted = false;
    int numberRetry = 0;
    while (!fileDeleted && numberRetry < 10) {
      Thread.sleep(100);
      fileDeleted = !p.toFile().exists();
      numberRetry++;
    }

    assertTrue(fileDeleted);
  }

}

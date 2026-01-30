package ca.gc.aafc.dina.export.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import ca.gc.aafc.dina.export.api.BaseIntegrationTest;
import ca.gc.aafc.dina.export.api.DinaExportModuleApiLauncher;
import ca.gc.aafc.dina.export.api.config.DataExportConfig;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.testsupport.DatabaseSupportService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.inject.Inject;
import javax.persistence.NoResultException;

@SpringBootTest(classes = DinaExportModuleApiLauncher.class,
  properties = {
    "dina.export.expiredExportMaxAge=12d",
    "dina.export.expiredExportCronExpression=*/1 * * * * *"
  })
public class ExpiredExportRemovalServiceIT extends BaseIntegrationTest {

  public static final String INTERVAL_2_WEEKS = "UPDATE data_export SET created_on = created_on - interval '2 weeks'";
  public static final String INTERVAL_3_WEEKS = "UPDATE data_export SET created_on = created_on - interval '3 weeks'";

  @Inject
  protected DataExportConfig dataExportConfig;

  @Inject
  protected DatabaseSupportService dbSupportService;

  @Test
  public void onRecordExpired_recordRemoved() throws IOException, InterruptedException {

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
        .filename(p.getFileName().toString())
        .build();

      em.persist(upload);
      em.createNativeQuery(INTERVAL_2_WEEKS).executeUpdate(); // Mock record created in the past
      em.flush();
    });

    // wait for the Scheduled job to run
    boolean fileDeleted = false;
    int numberRetry = 0;
    while (!fileDeleted && numberRetry < 100) {
      Thread.sleep(100);
      fileDeleted = !p.toFile().exists();
      numberRetry++;
    }
    assertTrue(fileDeleted);

    DataExport dataExportFromDB = dbSupportService.findUnique(DataExport.class, "uuid", testUUID);
    assertEquals(DataExport.ExportStatus.EXPIRED, dataExportFromDB.getStatus());

    // make sure the removal will be trigger (12 days + 1 week)
    dbSupportService.runInNewTransaction(em -> {
      em.createNativeQuery(INTERVAL_3_WEEKS).executeUpdate(); // Mock record created in the past
    });

    // wait for the Scheduled job to run another time
    Thread.sleep(1000);
    assertThrows(NoResultException.class,
      () -> dbSupportService.findUnique(DataExport.class, "uuid", testUUID));

  }
}

package ca.gc.aafc.dina.export.api.generator;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import jakarta.persistence.NoResultException;

import org.junit.jupiter.api.Test;

import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.service.DataExportStatusService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataExportGeneratorTest {

  @Test
  void waitForRecordRetriesUntilStatusIsFound() {
    DataExportStatusService statusService = mock(DataExportStatusService.class);
    UUID uuid = UUID.randomUUID();
    when(statusService.findStatus(uuid))
      .thenThrow(new NoResultException(), new NoResultException(), new NoResultException(), new NoResultException())
      .thenReturn(DataExport.ExportStatus.NEW);

    TestDataExportGenerator generator = new TestDataExportGenerator(statusService);

    assertEquals(DataExport.ExportStatus.NEW, generator.waitForRecord(uuid));
    verify(statusService, times(5)).findStatus(uuid);
  }

  private static class TestDataExportGenerator extends DataExportGenerator {

    TestDataExportGenerator(DataExportStatusService statusService) {
      super(statusService);
    }

    @Override
    public String generateFilename(DataExport dataExport) {
      return "";
    }

    @Override
    public CompletableFuture<UUID> export(DataExport dataExport) {
      return CompletableFuture.completedFuture(dataExport.getUuid());
    }

    @Override
    public void deleteExport(DataExport dataExport) {
    }

    @Override
    public DataExport.ExportStatus waitForRecord(UUID uuid) {
      return super.waitForRecord(uuid);
    }
  }
}

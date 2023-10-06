package ca.gc.aafc.dina.export.api.service;

import java.util.UUID;
import lombok.NonNull;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.jpa.BaseDAO;

/**
 * Service class that is used to manipulate export status.
 * It creates its own transaction to allow usage within an asynchronous task.
 */
@Service
public class DataExportStatusService {

  private static final int MAX_RETRY = 100;
  private static final int RETRY_SLEEP = 100;

  private final BaseDAO baseDAO;

  public DataExportStatusService(@NonNull BaseDAO baseDAO) {
    this.baseDAO = baseDAO;
  }

  @Transactional
  public void updateStatus(UUID uuid, DataExport.ExportStatus newStatus) {
    DataExport da = baseDAO.findOneByNaturalId(uuid, DataExport.class);
    da.setStatus(newStatus);
    baseDAO.update(da);
  }

  /**
   * to be improved
   * @param uuid
   * @return
   */
  @Transactional(
    readOnly = true
  )
  public DataExport waitForEntity(UUID uuid) {
    DataExport da = null;
    int retry = 0;
    while(da == null && retry < MAX_RETRY) {
      da = baseDAO.findOneByNaturalId(uuid, DataExport.class);
      try {
        Thread.sleep(RETRY_SLEEP);
        retry++;
      } catch (InterruptedException e) {
        return null;
      }
    }
    return da;
  }

}

package ca.gc.aafc.dina.export.api.service;

import java.util.UUID;
import javax.persistence.NoResultException;

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

  private final BaseDAO baseDAO;

  public DataExportStatusService(BaseDAO baseDAO) {
    this.baseDAO = baseDAO;
  }

  @Transactional
  public void updateStatus(UUID uuid, DataExport.ExportStatus newStatus) {
    DataExport da = baseDAO.findOneByNaturalId(uuid, DataExport.class);
    da.setStatus(newStatus);
    baseDAO.update(da);
  }

  /**
   * Find the current ExportStatus of a DataExport by UUID.
   * @param uuid
   * @return the status or throws NoResultException if no record can be found with the provided uuid
   */
  @Transactional(
    readOnly = true
  )
  public DataExport.ExportStatus findStatus(UUID uuid) {
    DataExport da = baseDAO.findOneByNaturalId(uuid, DataExport.class);
    if(da == null) {
      throw new NoResultException();
    }
    return da.getStatus();
  }

}

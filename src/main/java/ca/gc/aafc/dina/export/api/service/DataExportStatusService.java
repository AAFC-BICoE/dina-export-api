package ca.gc.aafc.dina.export.api.service;

import java.util.UUID;
import javax.transaction.Transactional;
import lombok.NonNull;

import org.springframework.stereotype.Service;

import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.jpa.BaseDAO;

/**
 * Service class that is used to manipulate export status.
 * It creates its own transaction to allow usage within an asynchronous task.
 */
@Service
public class DataExportStatusService {

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

}

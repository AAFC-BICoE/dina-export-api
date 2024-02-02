package ca.gc.aafc.dina.export.api.generator;

import javax.inject.Inject;
import javax.transaction.Transactional;

import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.service.DataExportService;

/**
 * Test helper class to wrap dataExportService in a transaction
 */
@Component
public class DataExportServiceTransactionWrapper {

  @Inject
  private DataExportService dataExportService;

  @Transactional
  public void createEntity(DataExport de) {
    dataExportService.create(de);
  }

}

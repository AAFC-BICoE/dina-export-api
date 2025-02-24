package ca.gc.aafc.dina.export.api.testsupport.factories;

import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.entity.DataExportTemplate;
import ca.gc.aafc.dina.testsupport.factories.TestableEntityFactory;

import java.util.UUID;

public class DataExportTemplateFactory {

  public static DataExportTemplate.DataExportTemplateBuilder newDataExportTemplate() {
    return DataExportTemplate.builder()
      .uuid(UUID.randomUUID())
      .group("aafc")
      .exportType(DataExport.ExportType.TABULAR_DATA)
      .publiclyReleasable(false)
      .restrictToCreatedBy(false)
      .name(TestableEntityFactory.generateRandomNameLettersOnly(7))
      .createdBy("test user");
  }

}

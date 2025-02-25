package ca.gc.aafc.dina.export.api.testsupport.fixtures;

import ca.gc.aafc.dina.export.api.dto.DataExportTemplateDto;
import ca.gc.aafc.dina.export.api.entity.DataExport;

public class DataExportTemplateTestFixture {

  public static final String GROUP = "my-group";

  private DataExportTemplateTestFixture() {
    // utility class
  }

  public static DataExportTemplateDto.DataExportTemplateDtoBuilder newDataExportTemplate() {
    return DataExportTemplateDto.builder()
      .exportType(DataExport.ExportType.TABULAR_DATA)
      .publiclyReleasable(false)
      .restrictToCreatedBy(false)
      .group(GROUP);
  }

}

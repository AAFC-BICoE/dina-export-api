package ca.gc.aafc.dina.export.api.testsupport.fixtures;

import org.springframework.http.MediaType;

import ca.gc.aafc.dina.export.api.entity.ReportTemplate;
import ca.gc.aafc.dina.testsupport.factories.TestableEntityFactory;
import ca.gc.aafc.dina.export.api.dto.ReportTemplateDto;

public class ReportTemplateTestFixture {

  public static final String GROUP = "group 1";

  private ReportTemplateTestFixture() {
  }

  public static ReportTemplateDto.ReportTemplateDtoBuilder newReportTemplate() {
    return ReportTemplateDto.builder()
      .group(GROUP)
      .reportType(ReportTemplate.ReportType.MATERIAL_SAMPLE_LABEL)
      .outputMediaType(MediaType.APPLICATION_PDF_VALUE)
      .templateOutputMediaType(MediaType.TEXT_HTML_VALUE)
      .templateFilename("test.ftl")
      .reportVariables(new String[]{"a", ""})
      .name(TestableEntityFactory.generateRandomNameLettersOnly(8));
  }
}

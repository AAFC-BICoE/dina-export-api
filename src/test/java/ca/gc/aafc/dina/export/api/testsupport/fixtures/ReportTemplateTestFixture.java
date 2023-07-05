package ca.gc.aafc.dina.export.api.testsupport.fixtures;

import org.springframework.http.MediaType;

import ca.gc.aafc.dina.testsupport.factories.TestableEntityFactory;
import ca.gc.aafc.dina.export.api.dto.ReportTemplateDto;

public class ReportTemplateTestFixture {

  public static final String GROUP = "group 1";

  private ReportTemplateTestFixture() {
  }

  public static ReportTemplateDto.ReportTemplateDtoBuilder newReportTemplate() {
    return ReportTemplateDto.builder()
      .group(GROUP)
      .outputMediaType(MediaType.APPLICATION_PDF_VALUE)
      .templateOutputMediaType(MediaType.TEXT_HTML_VALUE)
      .templateFilename("test.ftl")
      .name(TestableEntityFactory.generateRandomNameLettersOnly(8));
  }
}

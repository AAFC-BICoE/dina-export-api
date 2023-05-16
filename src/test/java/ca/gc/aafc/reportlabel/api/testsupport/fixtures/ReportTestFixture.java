package ca.gc.aafc.reportlabel.api.testsupport.fixtures;

import org.springframework.http.MediaType;

import ca.gc.aafc.dina.testsupport.factories.TestableEntityFactory;
import ca.gc.aafc.reportlabel.api.dto.ReportTemplateDto;

public class ReportTestFixture {

  public static final String GROUP = "group 1";

  private ReportTestFixture() {
  }

  public static ReportTemplateDto.ReportTemplateDtoBuilder newReportTemplate() {
    return ReportTemplateDto.builder()
      .group(GROUP)
      .outputMediaType(MediaType.APPLICATION_PDF_VALUE)
      .templateFilename("test.ftl")
      .name(TestableEntityFactory.generateRandomNameLettersOnly(8));
  }
}

package ca.gc.aafc.reportlabel.api.testsupport.fixtures;

import ca.gc.aafc.reportlabel.api.dto.ReportRequestDto;

public final class ReportRequestTestFixture {

  public static final String GROUP = "group 1";

  private ReportRequestTestFixture() {
  }

  public static ReportRequestDto.ReportRequestDtoBuilder newReportRequest() {
    return ReportRequestDto.builder()
        .group(GROUP);
  }
}

package ca.gc.aafc.reportlabel.api.testsupport.factories;

import java.util.UUID;

import org.springframework.http.MediaType;

import ca.gc.aafc.dina.testsupport.factories.TestableEntityFactory;
import ca.gc.aafc.reportlabel.api.entity.ReportTemplate;

public class ReportFactory implements TestableEntityFactory<ReportTemplate> {

  /**
   * Static method that can be called to return a configured builder that can be
   * further customized to return the actual entity object, call the .build()
   * method on a builder.
   *
   * @return Pre-configured builder with all mandatory fields set
   */
  public static ReportTemplate.ReportTemplateBuilder newReport() {
    return ReportTemplate.builder()
      .uuid(UUID.randomUUID())
      .group("aafc")
      .name(TestableEntityFactory.generateRandomNameLettersOnly(7))
      .outputMediaType(MediaType.APPLICATION_PDF_VALUE)
      .templateFilename("test.ftl")
      .createdBy("test user");
  }

  @Override
  public ReportTemplate getEntityInstance() {
    return newReport().build();
  }
}
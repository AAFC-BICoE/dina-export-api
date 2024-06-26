package ca.gc.aafc.dina.export.api.generator;

import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

@Service
public class FreemarkerReportGenerator implements ReportGenerator {

  private final Configuration freemarkerConfiguration;

  public FreemarkerReportGenerator(Configuration freemarkerConfiguration) {
    this.freemarkerConfiguration = freemarkerConfiguration;
    // By default, allow nothing. We can eventually have an allowed list by using OptInTemplateClassResolver
    this.freemarkerConfiguration.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
  }

  @Override
  public void generateReport(String templateIdentifier, Map<String, Object> variables, Writer outputWriter) throws IOException {
    try {
      Template template = freemarkerConfiguration.getTemplate(templateIdentifier);
      String output = FreeMarkerTemplateUtils.processTemplateIntoString(
              template, variables);
      outputWriter.write(output);
    } catch (TemplateException e) {
      throw new IOException(e);
    }
  }
}

package ca.gc.aafc.dina.export.api.generator;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

public interface ReportGenerator {

  /**
   *
   * @param templateIdentifier filename of the template available in the classpath
   * @param variables all variables to send to the template
   * @param outputWriter output to write the result. The caller is responsible to close.
   * @throws IOException
   */
  void generateReport(String templateIdentifier, Map<String, Object> variables, Writer outputWriter) throws IOException;

}

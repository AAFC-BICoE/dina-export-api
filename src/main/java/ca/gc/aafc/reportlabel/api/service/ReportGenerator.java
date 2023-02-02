package ca.gc.aafc.reportlabel.api.service;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

public interface ReportGenerator {

  void generateReport(String templateIdentifier, Map<String, Object> variables, Writer outputWriter) throws IOException;

}

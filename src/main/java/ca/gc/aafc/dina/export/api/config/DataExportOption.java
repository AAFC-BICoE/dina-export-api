package ca.gc.aafc.dina.export.api.config;

import java.util.Map;

import org.apache.commons.collections4.MapUtils;

/**
 * Export option keys used in DataExport.exportOptions
 */
public final class DataExportOption {

  public static final String ENABLE_PACKAGING = "enablePackaging";
  public static final String OPTION_COLUMN_SEPARATOR = "columnSeparator";

  private DataExportOption() {
    // utility class
  }

  public static boolean getOptionAsBool(Map<String, String> exportOptions, String option) {
    if (MapUtils.isEmpty(exportOptions)) {
      return false;
    }
    return Boolean.parseBoolean(exportOptions.getOrDefault(option, "false"));
  }

}

package ca.gc.aafc.dina.export.api.config;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Data Export function configuration.
 * 
 * @param functionDef
 * @param params
 */
public record DataExportFunction(FunctionDef functionDef, Map<String, Object> params) {

  public static final String CONCAT_PARAM_ITEMS = "items";
  public static final String CONCAT_PARAM_CONSTANTS = "constants";
  public static final String CONCAT_PARAM_SEPARATOR = "separator";
  public static final String CONCAT_DEFAULT_SEP = ",";

  public static final String CONVERT_COORDINATES_DD_PARAM = "column";
  public static final String COORDINATES_DD_FORMAT = "%f,%f";

  public enum FunctionDef {
    CONCAT(DataExportFunction::concatParamsValidator),
    CONVERT_COORDINATES_DD(DataExportFunction::convertCoordinatesDDParamsValidator);

    private final Predicate<Map<String, Object>> paramsValidator;

    FunctionDef(Predicate<Map<String, Object>> paramsValidator) {
      this.paramsValidator = paramsValidator;
    }

    public boolean areParamsValid(Map<String, Object> params) {
      return paramsValidator.test(params);
    }
  }

  public boolean areParamsValid() {
    return functionDef.areParamsValid(params);
  }

  @SuppressWarnings("unchecked")
  public java.util.List<String> getParamAsList(String paramKey) {
    return isValidNonEmptyList(params, paramKey) ? (java.util.List<String>) params.get(paramKey) :
      List.of();
  }

  @SuppressWarnings("unchecked")
  public java.util.Map<String, String> getParamAsMap(String paramKey) {
    return isValidNonEmptyMap(params, paramKey) ?
      (java.util.Map<String, String>) params.get(paramKey) : Map.of();
  }

  public String getParamAsString(String paramKey) {
    return params.getOrDefault(paramKey, "").toString();
  }

  private static boolean isValidNonEmptyList(Map<String, Object> params, String paramKey) {
    return params.containsKey(paramKey) &&
      params.get(paramKey) instanceof java.util.Collection<?> collection &&
      !collection.isEmpty();
  }

  private static boolean isValidNonEmptyMap(Map<String, Object> params, String paramKey) {
    return params.containsKey(paramKey) &&
      params.get(paramKey) instanceof java.util.Map<?, ?> map &&
      !map.isEmpty();
  }

  private static boolean concatParamsValidator(Map<String, Object> params) {

    if (params == null || params.isEmpty()) {
      return false;
    }

    // At least one of "items" or "constants" must be present
    boolean hasConstants = params.containsKey(CONCAT_PARAM_CONSTANTS);

    // items value is mandatory
    if (!isValidNonEmptyList(params, CONCAT_PARAM_ITEMS)) {
      return false;
    }

    if (hasConstants && !isValidNonEmptyMap(params, CONCAT_PARAM_CONSTANTS)) {
      return false;
    }

    return true;
  }

  private static boolean convertCoordinatesDDParamsValidator(Map<String, Object> params) {

    if (params == null || params.isEmpty()) {
      return false;
    }
    return params.containsKey(CONVERT_COORDINATES_DD_PARAM);
  }
}

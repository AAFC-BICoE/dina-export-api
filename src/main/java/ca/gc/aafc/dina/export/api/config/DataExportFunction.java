package ca.gc.aafc.dina.export.api.config;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Data Export function configuration.
 *
 * @param functionDef the function definition
 * @param params configuration parameters
 *
 * @implNote Validation is applied on creation
 * Getter methods return empty/safe defaults.
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

  public DataExportFunction {
    Objects.requireNonNull(functionDef, "functionDef cannot be null");
    Objects.requireNonNull(params, "params cannot be null");
    if (!functionDef.areParamsValid(params)) {
      throw new IllegalArgumentException("Invalid parameters for " + functionDef);
    }
  }

  /**
   * Retrieves a parameter as a list of strings.
   *
   * <p>Safely extracts a list parameter from the configuration, performing type validation
   * to ensure the parameter exists and is a non-empty collection. If validation fails,
   * returns an empty immutable list.
   *
   * @param paramKey the parameter key to retrieve
   * @return a list of strings if the parameter exists and is a valid non-empty collection;
   *         an empty immutable list otherwise
   *
   * @see #isValidNonEmptyList(Map, String)
   */
  @SuppressWarnings("unchecked")
  public java.util.List<String> getParamAsList(String paramKey) {
    return isValidNonEmptyList(params, paramKey) ? (java.util.List<String>) params.get(paramKey) :
      List.of();
  }

  /**
   * Retrieves a parameter as a map with string keys and values.
   *
   * <p>Safely extracts a map parameter from the configuration, performing type validation
   * to ensure the parameter exists and is a non-empty map. If validation fails,
   * returns an empty immutable map.
   *
   * @param paramKey the parameter key to retrieve
   * @return a map with string keys and values if the parameter exists and is a valid non-empty map;
   *         an empty immutable map otherwise
   *
   * @see #isValidNonEmptyMap(Map, String)
   */
  @SuppressWarnings("unchecked")
  public java.util.Map<String, String> getParamAsMap(String paramKey) {
    return isValidNonEmptyMap(params, paramKey) ?
      (java.util.Map<String, String>) params.get(paramKey) : Map.of();
  }

  /**
   * Retrieves a parameter as a string.
   *
   * <p>Extracts a parameter value and returns its string representation.
   * If the parameter is not present or is null, returns an empty string.
   *
   * @param paramKey the parameter key to retrieve
   * @return the string representation of the parameter value, or an empty string if not present
   */
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

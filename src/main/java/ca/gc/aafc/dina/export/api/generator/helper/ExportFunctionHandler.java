package ca.gc.aafc.dina.export.api.generator.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.gc.aafc.dina.export.api.config.DataExportFunction;
import ca.gc.aafc.dina.json.JsonHelper;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.IteratorUtils;

/**
 * Handles the execution of configured export function against entity attributes.
 */
@Log4j2
public final class ExportFunctionHandler {

  private ExportFunctionHandler() {
    // utility class
  }

  /**
   * Applies all configured export functions to the given attributes node.
   * @param attributes the attributes to transform
   * @param columnFunctions map of column name to function definition (may be null/empty)
   */
  public static void applyExportFunctions(ObjectNode attributes,
                                           Map<String, DataExportFunction> columnFunctions) {
    if (MapUtils.isEmpty(columnFunctions)) {
      return;
    }

    for (var functionDef : columnFunctions.entrySet()) {
      String result = executeFunction(attributes, functionDef.getValue());
      if (result != null) {
        attributes.put(functionDef.getKey(), result);
      }
    }
  }

  private static String executeFunction(ObjectNode attributes, DataExportFunction function) {
    return switch (function.functionDef()) {
      case CONCAT -> handleConcat(attributes, function);
      case CONVERT_COORDINATES_DD -> handleConvertCoordinatesDD(attributes, function);
      default -> {
        log.warn("Unknown function type: {}", function.functionDef());
        yield null;
      }
    };
  }

  /**
   * Concatenates attribute values and/or constants using a separator.
   *
   * @param attributes the source attributes
   * @param function the concat function definition with items, constants, and separator params
   * @return the concatenated string
   */
  static String handleConcat(ObjectNode attributes, DataExportFunction function) {
    List<String> items = function.getParamAsList(DataExportFunction.CONCAT_PARAM_ITEMS);
    Map<String, String> constants = function.getParamAsMap(DataExportFunction.CONCAT_PARAM_CONSTANTS);
    String separator = function.params()
      .getOrDefault(DataExportFunction.CONCAT_PARAM_SEPARATOR, DataExportFunction.CONCAT_DEFAULT_SEP)
      .toString();

    List<String> parts = new ArrayList<>();
    for (String col : items) {
      if (attributes.has(col)) {
        parts.add(JsonHelper.safeAsText(attributes, col));
      } else if (constants.containsKey(col)) {
        parts.add(constants.get(col));
      }
    }
    return String.join(separator, parts);
  }

  /**
   * Converts a geo_point stored as [longitude, latitude] to a "lat,lon" decimal degree string.
   *
   * @param attributes the source attributes
   * @param function the function definition with the column param
   * @return formatted "lat,lon" string, or null if coordinates are invalid
   */
  static String handleConvertCoordinatesDD(ObjectNode attributes, DataExportFunction function) {
    String column = function.getParamAsString(DataExportFunction.CONVERT_COORDINATES_DD_PARAM);
    JsonNode coordinates = attributes.get(column);

    if (coordinates != null && coordinates.isArray()) {
      List<JsonNode> longLatNode = IteratorUtils.toList(coordinates.iterator());
      if (longLatNode.size() == 2) {
        return String.format(DataExportFunction.COORDINATES_DD_FORMAT,
          longLatNode.get(1).asDouble(), longLatNode.get(0).asDouble());
      }
    }

    log.debug("Invalid Coordinates format. Array of doubles in form of [lon,lat] expected");
    return null;
  }
}

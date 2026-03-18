package ca.gc.aafc.dina.export.api.entity;

import java.util.List;

import lombok.NonNull;

/**
 * Schema definition for a single entity type in an export.
 * Defines which columns to export and optional aliases for column headers.
 * 
 * @param columns List of field names to export from the entity
 * @param aliases Optional list of header aliases. Must be:
 *                - null: all columns use their field names
 *                - exactly same length as columns: each position maps to corresponding column
 *                - contain "" or null in positions where you want to keep the column name
 *                Example: columns=["id","name","group"], aliases=["","Created By",""] → headers: "id", "Created By", "group"
 *                Invalid: aliases with different length than columns will throw IllegalArgumentException
 */
public record DataExportSchemaEntry(@NonNull List<String> columns, List<String> aliases) {
  
  /**
   * Compact constructor that validates the aliases array length matches columns length.
   * This ensures the structural invariant is enforced at object creation time.
   * 
   * @throws IllegalArgumentException if aliases is non-null, non-empty, and length doesn't match columns
   */
  public DataExportSchemaEntry {
    if (aliases != null && !aliases.isEmpty() && aliases.size() != columns.size()) {
      throw new IllegalArgumentException(
        String.format("Aliases array length (%d) must match columns array length (%d). " +
          "Use empty strings \"\" for positions where you want to keep the column name.",
          aliases.size(), columns.size()));
    }
  }
}

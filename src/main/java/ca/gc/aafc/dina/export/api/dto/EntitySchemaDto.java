package ca.gc.aafc.dina.export.api.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for entity schema configuration.
 * Defines which columns to export and optional aliases for column headers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitySchemaDto {
  
  /**
   * List of field names to export from the entity.
   */
  private List<String> columns;
  
  /**
   * Optional list of header aliases.
   * Must be null (all columns use field names) or exact same length as columns array.
   * Within the aliases array, use null or empty strings for positions where you want to keep the field name.
   * Example: columns=["id","name","group"], aliases=["","Display Name",""] → headers: "id", "Display Name", "group"
   * Invalid: aliases=["Display Name"] with 3 columns will throw validation error.
   */
  private List<String> aliases;
}

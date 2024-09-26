package ca.gc.aafc.dina.export.api.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.NoArgsConstructor;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.mapper.DinaFieldAdapter;

import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.MAP_TYPEREF;

/**
 * Responsible to provide class definitions of the DinaFieldAdapter
 */
public final class FieldsAdapter {

  private FieldsAdapter() {
    // utility calss
  }

  private static final ObjectMapper OBJ_MAPPER = new ObjectMapper();

  /**
   * Field adapter between Dto query (String) and Entity query (Map)
   */
  @NoArgsConstructor
  public static final class DataExportQueryFieldAdapter
    implements DinaFieldAdapter<DataExportDto, DataExport, String, Map<String, Object>> {

    @Override
    public String toDTO(Map<String, Object> query) {
      try {
        return query == null ? null : OBJ_MAPPER.writeValueAsString(query);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Map<String, Object> toEntity(String query) {
      if (StringUtils.isBlank(query)) {
        return null;
      }
      try {
        return OBJ_MAPPER.readValue(query, MAP_TYPEREF);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Consumer<Map<String, Object>> entityApplyMethod(DataExport entityRef) {
      return entityRef::setQuery;
    }

    @Override
    public Consumer<String> dtoApplyMethod(DataExportDto dtoRef) {
      return dtoRef::setQuery;
    }

    @Override
    public Supplier<Map<String, Object>> entitySupplyMethod(DataExport entityRef) {
      return entityRef::getQuery;
    }

    @Override
    public Supplier<String> dtoSupplyMethod(DataExportDto dtoRef) {
      return dtoRef::getQuery;
    }
  }

  /**
    * Field adapter between Dto query (String) and Entity query (Map)
    */
  @NoArgsConstructor
  public static final class DataExportColumnsFieldAdapter
    implements DinaFieldAdapter<DataExportDto, DataExport, List<String>, String[]> {

    @Override
    public List<String> toDTO(String[] columns) {
      return columns == null ? null : Arrays.asList(columns);
    }

    @Override
    public String[] toEntity(List<String> columns) {
      return columns == null ? null : columns.toArray(String[]::new);
    }

    @Override
    public Consumer<String[]> entityApplyMethod(DataExport entityRef) {
      return entityRef::setColumns;
    }

    @Override
    public Consumer<List<String>> dtoApplyMethod(DataExportDto dtoRef) {
      return dtoRef::setColumns;
    }

    @Override
    public Supplier<String[]> entitySupplyMethod(DataExport entityRef) {
      return entityRef::getColumns;
    }

    @Override
    public Supplier<List<String>> dtoSupplyMethod(DataExportDto dtoRef) {
      return dtoRef::getColumns;
    }
  }

  @NoArgsConstructor
  public static final class DataExportColumnAliasesFieldAdapter
    implements DinaFieldAdapter<DataExportDto, DataExport, List<String>, String[]> {

    @Override
    public List<String> toDTO(String[] columnAliases) {
      return columnAliases == null ? null : Arrays.asList(columnAliases);
    }

    @Override
    public String[] toEntity(List<String> columnAliases) {
      return columnAliases == null ? null : columnAliases.toArray(String[]::new);
    }

    @Override
    public Consumer<String[]> entityApplyMethod(DataExport entityRef) {
      return entityRef::setColumnAliases;
    }

    @Override
    public Consumer<List<String>> dtoApplyMethod(DataExportDto dtoRef) {
      return dtoRef::setColumnAliases;
    }

    @Override
    public Supplier<String[]> entitySupplyMethod(DataExport entityRef) {
      return entityRef::getColumnAliases;
    }

    @Override
    public Supplier<List<String>> dtoSupplyMethod(DataExportDto dtoRef) {
      return dtoRef::getColumnAliases;
    }

  }
}

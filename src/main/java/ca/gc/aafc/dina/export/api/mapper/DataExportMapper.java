package ca.gc.aafc.dina.export.api.mapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.export.api.dto.DataExportDto;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.mapper.DinaMapperV2;

import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.MAP_TYPEREF;

@Mapper
public interface DataExportMapper extends DinaMapperV2<DataExportDto, DataExport> {

  ObjectMapper OBJ_MAPPER = new ObjectMapper();

  DataExportMapper INSTANCE = Mappers.getMapper(DataExportMapper.class);

  @Override
  @Mappings({
    @Mapping(source = "query", target = "query", qualifiedByName = "mapToJson"),
    @Mapping(source = "schema", target = "schema", qualifiedByName = "schemaToDto")
  })
  DataExportDto toDto(DataExport entity, @Context Set<String> provided, @Context String scope);

  @Override
  @Mappings({
    @Mapping(source = "query", target = "query", qualifiedByName = "jsonToMap"),
    @Mapping(source = "schema", target = "schema", qualifiedByName = "schemaToEntity")
  })
  DataExport toEntity(DataExportDto dto, @Context Set<String> provided, @Context String scope);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mappings({
    @Mapping(source = "query", target = "query", qualifiedByName = "jsonToMap"),
    @Mapping(source = "schema", target = "schema", qualifiedByName = "schemaToEntity")
  })
  void patchEntity(@MappingTarget DataExport entity, DataExportDto dto,
                   @Context Set<String> provided, @Context String scope);

  @Named("schemaToDto")
  static LinkedHashMap<String, List<String>> schemaToDto(LinkedHashMap<String, String[]> schema) {
    if (schema == null) {
      return null;
    }
    LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
    for (var entry : schema.entrySet()) {
      result.put(entry.getKey(), Arrays.asList(entry.getValue()));
    }
    return result;
  }

  @Named("schemaToEntity")
  static LinkedHashMap<String, String[]> schemaToEntity(LinkedHashMap<String, List<String>> schema) {
    if (schema == null) {
      return null;
    }
    LinkedHashMap<String, String[]> result = new LinkedHashMap<>();
    for (var entry : schema.entrySet()) {
      result.put(entry.getKey(), entry.getValue().toArray(new String[0]));
    }
    return result;
  }

  @Named("mapToJson")
  static String mapToJsonString(Map<String, Object> query) {
    try {
      return query == null ? null : OBJ_MAPPER.writeValueAsString(query);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Named("jsonToMap")
  static Map<String, Object> jsonStringToMap(String query) {
    if (StringUtils.isBlank(query)) {
      return null;
    }
    try {
      return OBJ_MAPPER.readValue(query, MAP_TYPEREF);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}

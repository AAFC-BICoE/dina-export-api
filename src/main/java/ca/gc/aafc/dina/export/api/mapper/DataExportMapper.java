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
import ca.gc.aafc.dina.export.api.dto.EntitySchemaDto;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.entity.EntitySchema;
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
  static LinkedHashMap<String, EntitySchemaDto> schemaToDto(LinkedHashMap<String, EntitySchema> schema) {
    if (schema == null) {
      return null;
    }
    LinkedHashMap<String, EntitySchemaDto> result = new LinkedHashMap<>();
    for (var entry : schema.entrySet()) {
      EntitySchema entitySchema = entry.getValue();
      result.put(entry.getKey(), EntitySchemaDto.builder()
        .columns(entitySchema.columns())
        .aliases(entitySchema.aliases())
        .build());
    }
    return result;
  }

  @Named("schemaToEntity")
  static LinkedHashMap<String, EntitySchema> schemaToEntity(LinkedHashMap<String, EntitySchemaDto> schema) {
    if (schema == null) {
      return null;
    }
    LinkedHashMap<String, EntitySchema> result = new LinkedHashMap<>();
    for (var entry : schema.entrySet()) {
      EntitySchemaDto dto = entry.getValue();
      result.put(entry.getKey(), new EntitySchema(dto.getColumns(), dto.getAliases()));
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

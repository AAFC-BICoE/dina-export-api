package ca.gc.aafc.dina.export.api.mapper;

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
    @Mapping(source = "query", target = "query", qualifiedByName = "mapToJson")
  })
  DataExportDto toDto(DataExport entity, @Context Set<String> provided, @Context String scope);

  @Override
  @Mappings({
    @Mapping(source = "query", target = "query", qualifiedByName = "jsonToMap")
  })
  DataExport toEntity(DataExportDto dto, @Context Set<String> provided, @Context String scope);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mappings({
    @Mapping(source = "query", target = "query", qualifiedByName = "jsonToMap")
  })
  void patchEntity(@MappingTarget DataExport entity, DataExportDto dto,
                   @Context Set<String> provided, @Context String scope);

  @Named("mapToJson")
  static String toDTO(Map<String, Object> query) {
    try {
      return query == null ? null : OBJ_MAPPER.writeValueAsString(query);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Named("jsonToMap")
  static Map<String, Object> toEntity(String query) {
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

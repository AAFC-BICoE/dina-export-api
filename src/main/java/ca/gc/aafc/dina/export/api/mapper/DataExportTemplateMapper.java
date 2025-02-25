package ca.gc.aafc.dina.export.api.mapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import ca.gc.aafc.dina.export.api.dto.DataExportTemplateDto;
import ca.gc.aafc.dina.export.api.entity.DataExportTemplate;
import ca.gc.aafc.dina.mapper.DinaMapperV2;

@Mapper
public interface DataExportTemplateMapper extends DinaMapperV2<DataExportTemplateDto, DataExportTemplate> {

  DataExportTemplateMapper INSTANCE = Mappers.getMapper(DataExportTemplateMapper.class);

  @Override
  DataExportTemplateDto toDto(DataExportTemplate entity,  @Context Set<String> provided,  @Context String scope);

  @Override
  DataExportTemplate toEntity(DataExportTemplateDto dto,  @Context Set<String> provided,  @Context String scope);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void patchEntity(@MappingTarget DataExportTemplate entity, DataExportTemplateDto dto,
                   @Context Set<String> provided, @Context String scope);

  default <K, V> Map<K, V> nullSafeMap(Map<K, V> map) {
    return map == null ? null : new LinkedHashMap<>(map);
  }
}

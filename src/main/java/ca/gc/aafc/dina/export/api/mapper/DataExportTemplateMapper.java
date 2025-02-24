package ca.gc.aafc.dina.export.api.mapper;

import java.util.Set;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import ca.gc.aafc.dina.export.api.dto.DataExportTemplateDto;
import ca.gc.aafc.dina.export.api.entity.DataExportTemplate;
import ca.gc.aafc.dina.mapper.DinaMapperV2;

@Mapper
public interface DataExportTemplateMapper extends DinaMapperV2<DataExportTemplateDto, DataExportTemplate> {

  DataExportTemplateMapper INSTANCE = Mappers.getMapper(DataExportTemplateMapper.class);

  @Override
  DataExportTemplateDto toDto(DataExportTemplate entity, Set<String> provided, String scope);
  @Override
  DataExportTemplate toEntity(DataExportTemplateDto dto, Set<String> provided, String scope);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void patchEntity(DataExportTemplate entity, DataExportTemplateDto dto,
                          Set<String> provided, String scope);
}

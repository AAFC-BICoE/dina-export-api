package ca.gc.aafc.dina.export.api.mapper;

import java.util.Set;

import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import ca.gc.aafc.dina.export.api.dto.ReportTemplateDto;
import ca.gc.aafc.dina.export.api.entity.ReportTemplate;
import ca.gc.aafc.dina.mapper.DinaMapperV2;

@Mapper
public interface ReportTemplateMapper extends DinaMapperV2<ReportTemplateDto, ReportTemplate> {

  ReportTemplateMapper INSTANCE = Mappers.getMapper(ReportTemplateMapper.class);

  @Override
  ReportTemplateDto toDto(ReportTemplate entity, @Context Set<String> provided, @Context String scope);

  @Override
  ReportTemplate toEntity(ReportTemplateDto dto,  @Context Set<String> provided,  @Context String scope);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void patchEntity(@MappingTarget ReportTemplate entity, ReportTemplateDto dto,
                   @Context Set<String> provided, @Context String scope);

}


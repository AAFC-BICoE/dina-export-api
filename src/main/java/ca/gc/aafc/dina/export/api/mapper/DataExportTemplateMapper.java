package ca.gc.aafc.dina.export.api.mapper;

import java.util.LinkedHashMap;
import java.util.Set;

import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import ca.gc.aafc.dina.export.api.dto.DataExportTemplateDto;
import ca.gc.aafc.dina.export.api.dto.EntitySchemaDto;
import ca.gc.aafc.dina.export.api.entity.DataExportTemplate;
import ca.gc.aafc.dina.export.api.entity.EntitySchema;
import ca.gc.aafc.dina.mapper.DinaMapperV2;

@Mapper
public interface DataExportTemplateMapper extends DinaMapperV2<DataExportTemplateDto, DataExportTemplate> {

  DataExportTemplateMapper INSTANCE = Mappers.getMapper(DataExportTemplateMapper.class);

  @Override
  @Mapping(source = "schema", target = "schema", qualifiedByName = "schemaToDto")
  DataExportTemplateDto toDto(DataExportTemplate entity,  @Context Set<String> provided,  @Context String scope);

  @Override
  @Mapping(source = "schema", target = "schema", qualifiedByName = "schemaToEntity")
  DataExportTemplate toEntity(DataExportTemplateDto dto,  @Context Set<String> provided,  @Context String scope);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(source = "schema", target = "schema", qualifiedByName = "schemaToEntity")
  void patchEntity(@MappingTarget DataExportTemplate entity, DataExportTemplateDto dto,
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

}

package ca.gc.aafc.dina.export.api.repository;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.export.api.dto.ExportRequestDto;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.service.DataExportService;
import ca.gc.aafc.dina.security.DinaAuthenticatedUser;
import ca.gc.aafc.dina.security.auth.GroupAuthorizationService;

import io.crnk.core.exception.MethodNotAllowedException;
import io.crnk.core.queryspec.QuerySpec;
import io.crnk.core.repository.ResourceRepository;
import io.crnk.core.resource.list.ResourceList;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Optional;
import javax.transaction.Transactional;

import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.MAP_TYPEREF;

@Repository
public class ExportRequestRepository implements ResourceRepository<ExportRequestDto, Serializable> {

  private final DataExportService dataExportService;
  private final Optional<DinaAuthenticatedUser> dinaAuthenticatedUser;
  private final ObjectMapper objMapper;

  public ExportRequestRepository(
    GroupAuthorizationService authorizationService,
    Optional<DinaAuthenticatedUser> dinaAuthenticatedUser,
    DataExportService dataExportService,
    ObjectMapper objMapper) {

    this.dataExportService = dataExportService;
    this.dinaAuthenticatedUser = dinaAuthenticatedUser;
    this.objMapper = objMapper;
  }

  @Override
  @Transactional
  public <S extends ExportRequestDto> S create(S s) {

    try {
      DataExport dataExport = DataExport.builder()
        .source(s.getSource())
        .query(objMapper.readValue(s.getQuery(), MAP_TYPEREF))
        .columns(s.getColumns().toArray(String[]::new))
        .createdBy(dinaAuthenticatedUser.isPresent() ? dinaAuthenticatedUser.get().getUsername() : "?")
        .build();

      dataExportService.create(dataExport);

      s.setUuid(dataExport.getUuid());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return s;
  }

  @Override
  public Class<ExportRequestDto> getResourceClass() {
    return ExportRequestDto.class;
  }

  @Override
  public ExportRequestDto findOne(Serializable serializable, QuerySpec querySpec) {
    return null;
  }

  @Override
  public ResourceList<ExportRequestDto> findAll(QuerySpec querySpec) {
    throw new MethodNotAllowedException("GET");
  }

  @Override
  public ResourceList<ExportRequestDto> findAll(Collection<Serializable> collection,
                                                QuerySpec querySpec) {
    throw new MethodNotAllowedException("GET");
  }

  @Override
  public <S extends ExportRequestDto> S save(S s) {
    throw new MethodNotAllowedException("PUT/PATCH");
  }

  @Override
  public void delete(Serializable serializable) {
    throw new MethodNotAllowedException("DELETE");
  }
}

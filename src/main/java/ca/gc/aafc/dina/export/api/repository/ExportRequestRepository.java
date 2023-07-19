package ca.gc.aafc.dina.export.api.repository;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.export.api.dto.ExportRequestDto;
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

@Repository
public class ExportRequestRepository implements ResourceRepository<ExportRequestDto, Serializable> {

  private final DataExportService dataExportService;

  public ExportRequestRepository(
    GroupAuthorizationService authorizationService,
    Optional<DinaAuthenticatedUser> dinaAuthenticatedUser,
    DataExportService dataExportService,
    ObjectMapper objMapper) {

    this.dataExportService = dataExportService;
  }

  @Override
  public <S extends ExportRequestDto> S create(S s) {

    try {
      DataExportService.ExportResult result = dataExportService.export(s.getSource(), s.getQuery(), s.getColumns());
      s.setUuid(result.resultIdentifier());
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

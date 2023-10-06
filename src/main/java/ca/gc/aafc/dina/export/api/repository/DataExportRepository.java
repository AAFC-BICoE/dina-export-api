package ca.gc.aafc.dina.export.api.repository;

import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.gc.aafc.dina.export.api.dto.DataExportDto;
import ca.gc.aafc.dina.export.api.entity.DataExport;
import ca.gc.aafc.dina.export.api.service.DataExportService;
import ca.gc.aafc.dina.mapper.DinaMapper;
import ca.gc.aafc.dina.repository.DinaRepository;
import ca.gc.aafc.dina.security.DinaAuthenticatedUser;
import ca.gc.aafc.dina.security.auth.ObjectOwnerAuthorizationService;
import ca.gc.aafc.dina.service.AuditService;

import io.crnk.core.exception.MethodNotAllowedException;
import java.io.Serializable;
import java.util.Optional;
import lombok.NonNull;

@Repository
public class DataExportRepository extends DinaRepository<DataExportDto, DataExport> {

  private final Optional<DinaAuthenticatedUser> dinaAuthenticatedUser;

  public DataExportRepository(
    @NonNull DataExportService dinaService,
    @NonNull ObjectOwnerAuthorizationService authorizationService,
    Optional<DinaAuthenticatedUser> dinaAuthenticatedUser,
    @NonNull Optional<AuditService> auditService,
    @NonNull BuildProperties buildProperties, ObjectMapper objMapper) {

    super(dinaService, authorizationService, auditService,
      new DinaMapper<>(DataExportDto.class),
      DataExportDto.class,
      DataExport.class,
      null, null, buildProperties, objMapper);
    this.dinaAuthenticatedUser = dinaAuthenticatedUser;
  }

  @Override
  public <S extends DataExportDto> S create(S resource) {
    dinaAuthenticatedUser.ifPresent( user -> resource.setCreatedBy(user.getUsername()));
    return super.create(resource);
  }

  @Override
  public <S extends DataExportDto> S save(S s) {
    throw new MethodNotAllowedException("PUT/PATCH");
  }

  @Override
  public void delete(Serializable serializable) {
    throw new MethodNotAllowedException("DELETE");
  }
}

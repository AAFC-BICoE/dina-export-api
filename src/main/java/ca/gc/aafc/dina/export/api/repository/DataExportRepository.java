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
import java.util.Optional;

@Repository
public class DataExportRepository extends DinaRepository<DataExportDto, DataExport> {

  private final Optional<DinaAuthenticatedUser> dinaAuthenticatedUser;

  public DataExportRepository(
    DataExportService dinaService,
    ObjectOwnerAuthorizationService authorizationService,
    Optional<DinaAuthenticatedUser> dinaAuthenticatedUser,
    Optional<AuditService> auditService,
    BuildProperties buildProperties, ObjectMapper objMapper) {

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

    // make sure uuid will be auto-generated
    resource.setUuid(null);

    // for now the repository can only create csv
    resource.setExportType(DataExport.ExportType.TABULAR_DATA);

    return super.create(resource);
  }

  @Override
  public <S extends DataExportDto> S save(S s) {
    throw new MethodNotAllowedException("PUT/PATCH");
  }

}

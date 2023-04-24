package ca.gc.aafc.reportlabel.api.repository;

import ca.gc.aafc.dina.entity.DinaEntity;
import ca.gc.aafc.dina.security.DinaAuthenticatedUser;
import ca.gc.aafc.dina.security.GroupAuthorizationService;
import ca.gc.aafc.reportlabel.api.dto.ReportRequestDto;
import ca.gc.aafc.reportlabel.api.service.ReportRequestService;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.crnk.core.exception.MethodNotAllowedException;
import io.crnk.core.queryspec.QuerySpec;
import io.crnk.core.repository.ResourceRepository;
import io.crnk.core.resource.list.ResourceList;
import java.io.IOException;
import lombok.NonNull;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReportRequestRepository implements ResourceRepository<ReportRequestDto, Serializable> {

  private final GroupAuthorizationService authorizationService;
  private final ReportRequestService reportRequestService;

  public ReportRequestRepository(
    @NonNull GroupAuthorizationService authorizationService,
    Optional<DinaAuthenticatedUser> dinaAuthenticatedUser,
    ReportRequestService reportRequestService,
    @NonNull BuildProperties props,
    @NonNull ObjectMapper objMapper
  ) {
    this.authorizationService = authorizationService;
    this.reportRequestService = reportRequestService;
  }

  @Override
  public <S extends ReportRequestDto> S create(S s) {
    authorizationService.authorizeCreate(new GroupOnlyEntity(s.getGroup()));
    try {
      s.setUuid(UUID.randomUUID());
      ReportRequestService.CodeGenerationOption result = reportRequestService.generateReport(s);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return s;
  }

  @Override
  public Class<ReportRequestDto> getResourceClass() {
    return ReportRequestDto.class;
  }

  @Override
  public ReportRequestDto findOne(Serializable serializable, QuerySpec querySpec) {
    return null;
  }

  @Override
  public ResourceList<ReportRequestDto> findAll(QuerySpec querySpec) {
    throw new MethodNotAllowedException("GET");
  }

  @Override
  public ResourceList<ReportRequestDto> findAll(Collection<Serializable> collection, QuerySpec querySpec) {
    throw new MethodNotAllowedException("GET");
  }

  @Override
  public <S extends ReportRequestDto> S save(S s) {
    throw new MethodNotAllowedException("PUT/PATCH");
  }

  @Override
  public void delete(Serializable serializable) {
    throw new MethodNotAllowedException("DELETE");
  }

  static class GroupOnlyEntity implements DinaEntity {
    private final String group;

    GroupOnlyEntity(String group) {
      this.group = group;
    }

    @Override
    public String getGroup() {
      return group;
    }

    @Override
    public Integer getId() {
      return null;
    }

    @Override
    public UUID getUuid() {
      return null;
    }

    @Override
    public String getCreatedBy() {
      return null;
    }

    @Override
    public OffsetDateTime getCreatedOn() {
      return null;
    }
  }

}

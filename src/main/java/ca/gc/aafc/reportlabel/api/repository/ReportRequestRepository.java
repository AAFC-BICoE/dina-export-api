package ca.gc.aafc.reportlabel.api.repository;

import ca.gc.aafc.dina.entity.DinaEntity;
import ca.gc.aafc.dina.json.JsonDocumentInspector;
import ca.gc.aafc.dina.security.DinaAuthenticatedUser;
import ca.gc.aafc.dina.security.auth.GroupAuthorizationService;
import ca.gc.aafc.dina.security.TextHtmlSanitizer;
import ca.gc.aafc.reportlabel.api.dto.ReportRequestDto;
import ca.gc.aafc.reportlabel.api.entity.ReportTemplate;
import ca.gc.aafc.reportlabel.api.service.ReportRequestService;
import ca.gc.aafc.reportlabel.api.service.ReportTemplateService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crnk.core.exception.MethodNotAllowedException;
import io.crnk.core.exception.ResourceNotFoundException;
import io.crnk.core.queryspec.QuerySpec;
import io.crnk.core.repository.ResourceRepository;
import io.crnk.core.resource.list.ResourceList;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import javax.transaction.Transactional;
import lombok.NonNull;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class ReportRequestRepository implements ResourceRepository<ReportRequestDto, Serializable> {

  protected static final TypeReference<Map<String, Object>> IT_OM_TYPE_REF = new TypeReference<>() {
  };

  private final GroupAuthorizationService authorizationService;

  private final ReportRequestService reportRequestService;
  private final ReportTemplateService reportService;
  private final ObjectMapper objMapper;

  public ReportRequestRepository(
    @NonNull GroupAuthorizationService authorizationService,
    Optional<DinaAuthenticatedUser> dinaAuthenticatedUser,
    ReportRequestService reportRequestService,
    ReportTemplateService reportService,
    @NonNull BuildProperties props,
    @NonNull ObjectMapper objMapper
  ) {
    this.authorizationService = authorizationService;
    this.reportRequestService = reportRequestService;
    this.reportService = reportService;
    this.objMapper = objMapper;
  }

  @Override
  public <S extends ReportRequestDto> S create(S s) {

    checkSubmittedData(s);

    authorizationService.authorizeCreate(new GroupOnlyEntity(s.getGroup()));

    UUID reportTemplateUUID = s.getReportTemplateUUID();
    ReportTemplate reportTemplateEntity = reportService.findOne(reportTemplateUUID, ReportTemplate.class);

    if(reportTemplateEntity == null) {
      throw new ResourceNotFoundException("ReportTemplate with ID " + reportTemplateUUID + " Not Found.");
    }

    try {
      ReportRequestService.ReportGenerationResult result = reportRequestService.generateReport(reportTemplateEntity, s);
      // return the identifier assigned by the service
      s.setUuid(result.resultIdentifier());
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

  protected <S extends ReportRequestDto> void checkSubmittedData(S resource) {
    Objects.requireNonNull(objMapper);
    Map<String, Object> convertedObj = objMapper.convertValue(resource, IT_OM_TYPE_REF);
    if (!JsonDocumentInspector.testPredicateOnValues(convertedObj, TextHtmlSanitizer::isSafeText)) {
      throw new IllegalArgumentException("Unaccepted value detected in attributes");
    }
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

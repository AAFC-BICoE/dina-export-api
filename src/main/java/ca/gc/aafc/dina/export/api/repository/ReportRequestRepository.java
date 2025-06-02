package ca.gc.aafc.dina.export.api.repository;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.boot.info.BuildProperties;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toedter.spring.hateoas.jsonapi.JsonApiModelBuilder;

import ca.gc.aafc.dina.dto.JsonApiDto;
import ca.gc.aafc.dina.exception.ResourceNotFoundException;
import ca.gc.aafc.dina.export.api.dto.ReportRequestDto;
import ca.gc.aafc.dina.export.api.dto.ReportTemplateDto;
import ca.gc.aafc.dina.export.api.entity.ReportTemplate;
import ca.gc.aafc.dina.export.api.service.ReportRequestService;
import ca.gc.aafc.dina.export.api.service.ReportTemplateService;
import ca.gc.aafc.dina.json.JsonDocumentInspector;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import ca.gc.aafc.dina.repository.JsonApiModelAssistant;
import ca.gc.aafc.dina.security.DinaAuthenticatedUser;
import ca.gc.aafc.dina.security.TextHtmlSanitizer;
import ca.gc.aafc.dina.security.auth.GroupAuth;
import ca.gc.aafc.dina.security.auth.GroupAuthorizationService;

import static ca.gc.aafc.dina.export.api.config.JacksonTypeReferences.MAP_TYPEREF;
import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@RestController
@RequestMapping(value = "${dina.apiPrefix:}", produces = JSON_API_VALUE)
public class ReportRequestRepository {

  private static final Safelist SIMPLE_TEXT = Safelist.simpleText();

  private final GroupAuthorizationService authorizationService;

  private final ReportRequestService reportRequestService;
  private final ReportTemplateService reportService;
  private final ObjectMapper objMapper;
  private final JsonApiModelAssistant<ReportRequestDto> jsonApiModelAssistant;

  public ReportRequestRepository(
    GroupAuthorizationService authorizationService,
    Optional<DinaAuthenticatedUser> dinaAuthenticatedUser,
    ReportRequestService reportRequestService,
    ReportTemplateService reportService,
    BuildProperties buildProperties,
    ObjectMapper objMapper
  ) {
    this.authorizationService = authorizationService;
    this.reportRequestService = reportRequestService;
    this.reportService = reportService;
    this.objMapper = objMapper;

    this.jsonApiModelAssistant = new JsonApiModelAssistant<>(buildProperties.getVersion());
  }

  protected Link generateLinkToResource(ReportRequestDto dto) {
    return Link.of(
      Objects.toString(dto.getJsonApiType(), "") + "/" + Objects.toString(dto.getJsonApiId(), ""));
  }

  @PostMapping(ReportRequestDto.TYPENAME)
  @Transactional
  public ResponseEntity<RepresentationModel<?>> onCreate(@RequestBody JsonApiDocument postedDocument)
    throws ResourceNotFoundException {

    ReportRequestDto dto = objMapper.convertValue(postedDocument.getAttributes(), ReportRequestDto.class);
    checkSubmittedData(dto);

    authorizationService.authorizeCreate(GroupAuth.of(dto.getGroup()));

    UUID reportTemplateUUID = dto.getReportTemplateUUID();
    ReportTemplate reportTemplateEntity = reportService.findOne(reportTemplateUUID, ReportTemplate.class);

    if (reportTemplateEntity == null) {
      throw ResourceNotFoundException.create(ReportTemplateDto.TYPENAME, reportTemplateUUID);
    }

    try {
      ReportRequestService.ReportGenerationResult result = reportRequestService.generateReport(reportTemplateEntity, dto);
      // return the identifier assigned by the service
      dto.setUuid(result.resultIdentifier());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    JsonApiDto.JsonApiDtoBuilder<ReportRequestDto> jsonApiDtoBuilder = JsonApiDto.builder();
    jsonApiDtoBuilder.dto(dto);

    JsonApiModelBuilder builder = jsonApiModelAssistant.createJsonApiModelBuilder(jsonApiDtoBuilder.build());
    builder.link(generateLinkToResource(dto));

    RepresentationModel<?> model = builder.build();
    URI uri = model.getRequiredLink(IanaLinkRelations.SELF).toUri();

    return ResponseEntity.created(uri).body(model);

  }

  protected <S extends ReportRequestDto> void checkSubmittedData(S resource) {
    Objects.requireNonNull(objMapper);
    Map<String, Object> convertedObj = objMapper.convertValue(resource, MAP_TYPEREF);
    if (!JsonDocumentInspector.testPredicateOnValues(convertedObj, ReportRequestRepository.supplyCheckSubmittedDataPredicate())) {
      throw new IllegalArgumentException("Unaccepted value detected in attributes");
    }
  }

  protected static Predicate<String> supplyCheckSubmittedDataPredicate() {
    return txt -> isSafeSimpleText(txt) || TextHtmlSanitizer.isAcceptableText(txt);
  }

  private static boolean isSafeSimpleText(String txt) {
    return StringUtils.isBlank(txt) || Jsoup.isValid(txt, SIMPLE_TEXT);
  }

}

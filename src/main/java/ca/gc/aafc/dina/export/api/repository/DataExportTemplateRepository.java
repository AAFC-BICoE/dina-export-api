package ca.gc.aafc.dina.export.api.repository;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import lombok.NonNull;

import org.springframework.boot.info.BuildProperties;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toedter.spring.hateoas.jsonapi.JsonApiModelBuilder;

import ca.gc.aafc.dina.dto.JsonApiDto;
import ca.gc.aafc.dina.exception.ResourceNotFoundException;
import ca.gc.aafc.dina.export.api.entity.DataExportTemplate;
import ca.gc.aafc.dina.export.api.mapper.DataExportTemplateMapper;
import ca.gc.aafc.dina.jsonapi.JsonApiDocument;
import ca.gc.aafc.dina.repository.DinaRepositoryV2;
import ca.gc.aafc.dina.security.DinaAuthenticatedUser;

import ca.gc.aafc.dina.export.api.dto.DataExportTemplateDto;
import ca.gc.aafc.dina.security.auth.DinaAuthorizationService;
import ca.gc.aafc.dina.service.AuditService;
import ca.gc.aafc.dina.service.DinaService;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping(value = "/api/v1", produces = JSON_API_VALUE)
public class DataExportTemplateRepository extends DinaRepositoryV2<DataExportTemplateDto, DataExportTemplate> {

  // Bean does not exist with keycloak disabled.
  private final DinaAuthenticatedUser authenticatedUser;

  public DataExportTemplateRepository(
    @NonNull DinaService<DataExportTemplate> dinaService,
    @NonNull DinaAuthorizationService authorizationService,
    Optional<DinaAuthenticatedUser> authenticatedUser,
    @NonNull Optional<AuditService> auditService,
    @NonNull BuildProperties buildProperties,
    ObjectMapper objMapper) {
    super(dinaService, authorizationService, auditService,
      DataExportTemplateMapper.INSTANCE,
      DataExportTemplateDto.class, DataExportTemplate.class,
      buildProperties, objMapper);

    this.authenticatedUser = authenticatedUser.orElse(null);
  }

  @GetMapping(DataExportTemplateDto.TYPENAME + "/{id}")
  public ResponseEntity<RepresentationModel<?>> handleFindOne(@PathVariable UUID id, HttpServletRequest req)
    throws ResourceNotFoundException {

    String queryString = decodeQueryString(req);

    JsonApiDto<DataExportTemplateDto> jsonApiDto = getOne(id, queryString);
    if (jsonApiDto == null) {
      return ResponseEntity.notFound().build();
    }

    JsonApiModelBuilder builder = createJsonApiModelBuilder(jsonApiDto);

    return ResponseEntity.ok(builder.build());
  }

  @GetMapping(DataExportTemplateDto.TYPENAME)
  public ResponseEntity<RepresentationModel<?>> handleFindAll(HttpServletRequest req) {
    String queryString = decodeQueryString(req);

    PagedResource<JsonApiDto<DataExportTemplateDto>> dtos;
    try {
      dtos = getAll(queryString);
    } catch (IllegalArgumentException iaEx) {
      return ResponseEntity.badRequest().build();
    }

    JsonApiModelBuilder builder = createJsonApiModelBuilder(dtos);

    return ResponseEntity.ok(builder.build());
  }

  @PostMapping(DataExportTemplateDto.TYPENAME)
  @Transactional
  public ResponseEntity<RepresentationModel<?>> handleCreate(@RequestBody
                                                             JsonApiDocument postedDocument)
    throws ResourceNotFoundException {

    if (postedDocument == null) {
      return ResponseEntity.badRequest().build();
    }

    UUID uuid = create(postedDocument, dto -> {
      if (authenticatedUser != null) {
        dto.setCreatedBy(authenticatedUser.getUsername());
      }
    });

    // reload dto
    JsonApiDto<DataExportTemplateDto> jsonApiDto = getOne(uuid, null);
    if (jsonApiDto == null) {
      return ResponseEntity.notFound().build();
    }
    JsonApiModelBuilder builder = createJsonApiModelBuilder(jsonApiDto);

    builder.link(linkTo(methodOn(DataExportTemplateRepository.class).handleFindOne(jsonApiDto.getDto().getUuid(), null)).withSelfRel());
    RepresentationModel<?> model = builder.build();

    URI uri = model.getRequiredLink(IanaLinkRelations.SELF).toUri();

    return ResponseEntity.created(uri).body(model);
  }

  @PatchMapping(DataExportTemplateDto.TYPENAME + "/{id}")
  @Transactional
  public ResponseEntity<RepresentationModel<?>> handleUpdate(@RequestBody
                                                             JsonApiDocument partialPatchDto,
                                                             @PathVariable UUID id) throws ResourceNotFoundException {

    // Sanity check
    if (!Objects.equals(id, partialPatchDto.getId())) {
      return ResponseEntity.badRequest().build();
    }

    update(partialPatchDto);

    // reload dto
    JsonApiDto<DataExportTemplateDto> jsonApiDto = getOne(partialPatchDto.getId(), null);
    if (jsonApiDto == null) {
      return ResponseEntity.notFound().build();
    }
    JsonApiModelBuilder builder = createJsonApiModelBuilder(jsonApiDto);

    return ResponseEntity.ok().body(builder.build());
  }

  @DeleteMapping(DataExportTemplateDto.TYPENAME + "/{id}")
  @Transactional
  public ResponseEntity<RepresentationModel<?>> handleDelete(@PathVariable UUID id) throws ResourceNotFoundException {
    delete(id);
    return ResponseEntity.noContent().build();
  }

}
